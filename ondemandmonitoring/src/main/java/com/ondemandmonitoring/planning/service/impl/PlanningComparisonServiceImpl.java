package com.ondemandmonitoring.planning.service.impl;

import com.ondemandmonitoring.common.exception.ApiException;
import com.ondemandmonitoring.common.exception.ErrorCode;
import com.ondemandmonitoring.drone.domain.DroneTelemetry;
import com.ondemandmonitoring.drone.repository.DroneTelemetryRepository;
import com.ondemandmonitoring.mission.domain.Mission;
import com.ondemandmonitoring.mission.domain.MissionDroneAssignment;
import com.ondemandmonitoring.mission.enums.FeasibilityStatus;
import com.ondemandmonitoring.mission.enums.PlanningAlgorithm;
import com.ondemandmonitoring.mission.repository.MissionDroneAssignmentRepository;
import com.ondemandmonitoring.mission.repository.MissionRepository;
import com.ondemandmonitoring.order.domain.Order;
import com.ondemandmonitoring.planning.dto.AlgorithmPlanningResult;
import com.ondemandmonitoring.planning.dto.EnergyEstimate;
import com.ondemandmonitoring.planning.dto.EnergyEstimateRequest;
import com.ondemandmonitoring.planning.dto.PlannedRoute;
import com.ondemandmonitoring.planning.dto.PlanningComparisonResult;
import com.ondemandmonitoring.planning.dto.PlanningComparisonInput;
import com.ondemandmonitoring.planning.dto.PlanningTradeoff;
import com.ondemandmonitoring.planning.dto.SimulationPoint;
import com.ondemandmonitoring.planning.dto.response.EnvironmentSample;
import com.ondemandmonitoring.planning.service.MissionEnergyEstimator;
import com.ondemandmonitoring.planning.service.PlanningComparisonService;
import com.ondemandmonitoring.planning.service.PlanningEnvironment;
import com.ondemandmonitoring.planning.service.RoutePlanner;
import com.ondemandmonitoring.planning.service.SimulationHomeProvider;
import java.util.List;
import java.util.Optional;
import org.locationtech.jts.geom.Point;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlanningComparisonServiceImpl implements PlanningComparisonService {

    private static final int EPSG_4326_SRID = 4326;

    private final MissionRepository missionRepository;
    private final MissionDroneAssignmentRepository missionDroneAssignmentRepository;
    private final DroneTelemetryRepository droneTelemetryRepository;
    private final RoutePlanner directRoutePlanner;
    private final RoutePlanner aStarShortestRoutePlanner;
    private final RoutePlanner aStarEnergyAwareRoutePlanner;
    private final SimulationHomeProvider simulationHomeProvider;
    private final PlanningEnvironment planningEnvironment;
    private final MissionEnergyEstimator missionEnergyEstimator;

    public PlanningComparisonServiceImpl(
            MissionRepository missionRepository,
            MissionDroneAssignmentRepository missionDroneAssignmentRepository,
            DroneTelemetryRepository droneTelemetryRepository,
            @Qualifier("directRoutePlanner") RoutePlanner directRoutePlanner,
            @Qualifier("aStarShortestRoutePlanner") RoutePlanner aStarShortestRoutePlanner,
            @Qualifier("aStarEnergyAwareRoutePlanner") RoutePlanner aStarEnergyAwareRoutePlanner,
            SimulationHomeProvider simulationHomeProvider,
            PlanningEnvironment planningEnvironment,
            MissionEnergyEstimator missionEnergyEstimator) {
        this.missionRepository = missionRepository;
        this.missionDroneAssignmentRepository = missionDroneAssignmentRepository;
        this.droneTelemetryRepository = droneTelemetryRepository;
        this.directRoutePlanner = directRoutePlanner;
        this.aStarShortestRoutePlanner = aStarShortestRoutePlanner;
        this.aStarEnergyAwareRoutePlanner = aStarEnergyAwareRoutePlanner;
        this.simulationHomeProvider = simulationHomeProvider;
        this.planningEnvironment = planningEnvironment;
        this.missionEnergyEstimator = missionEnergyEstimator;
    }

    @Override
    @Transactional(readOnly = true)
    public PlanningComparisonResult compare(String missionId) {
        Mission mission = missionRepository.findByIdWithOrder(missionId)
                .orElseThrow(() -> new ApiException(ErrorCode.MISSION_NOT_FOUND, "Mission not found: " + missionId));
        SimulationPoint home = simulationHomeProvider.home();
        SimulationPoint target = resolveTarget(missionId, mission.getOrder());
        Double availableBattery = resolveAvailableBatteryPercent(missionId).orElse(null);

        return compareResolved(missionId, mission.getMissionCode(), home, target, availableBattery, false);
    }

    @Override
    @Transactional(readOnly = true)
    public PlanningComparisonResult compare(PlanningComparisonInput input) {
        if (input == null) throw new IllegalArgumentException("Planning comparison input is required.");
        if (input.contextId() == null || input.contextId().isBlank()) {
            throw new IllegalArgumentException("Planning comparison context ID is required.");
        }
        if (!finite(input.homeX()) || !finite(input.homeY())
                || !finite(input.targetX()) || !finite(input.targetY())) {
            throw new IllegalArgumentException("Planning comparison coordinates must be finite.");
        }
        if (input.availableBatteryPercent() != null && !isValidBatteryPercent(input.availableBatteryPercent())) {
            throw new IllegalArgumentException("Available battery percent must be between 0 and 100.");
        }
        return compareResolved(
                input.contextId(), null,
                new SimulationPoint(input.homeX(), input.homeY()),
                new SimulationPoint(input.targetX(), input.targetY()),
                input.availableBatteryPercent(), true);
    }

    private PlanningComparisonResult compareResolved(
            String contextId,
            String missionCode,
            SimulationPoint home,
            SimulationPoint target,
            Double availableBattery,
            boolean validateExplicitHome) {
        long comparisonStarted = System.nanoTime();
        double homeWorldZ = resolveHomeWorldZ(home, validateExplicitHome);

        AlgorithmPlanningResult direct = run(
                PlanningAlgorithm.DIRECT, directRoutePlanner, home, target, homeWorldZ, availableBattery);
        AlgorithmPlanningResult shortest = run(
                PlanningAlgorithm.ASTAR_SHORTEST, aStarShortestRoutePlanner, home, target, homeWorldZ, availableBattery);
        AlgorithmPlanningResult energyAware = run(
                PlanningAlgorithm.ASTAR_ENERGY_AWARE, aStarEnergyAwareRoutePlanner,
                home, target, homeWorldZ, availableBattery);

        return new PlanningComparisonResult(
                contextId,
                missionCode,
                home.x(),
                home.y(),
                target.x(),
                target.y(),
                homeWorldZ,
                List.of(direct, shortest, energyAware),
                tradeoff(shortest, energyAware),
                elapsedMs(comparisonStarted));
    }

    private AlgorithmPlanningResult run(
            PlanningAlgorithm algorithm,
            RoutePlanner planner,
            SimulationPoint home,
            SimulationPoint target,
            double homeWorldZ,
            Double availableBattery) {
        long started = System.nanoTime();
        PlannedRoute route = planner.plan(home.x(), home.y(), target.x(), target.y());
        long planningTimeMs = elapsedMs(started);
        if (!route.feasible()) {
            return new AlgorithmPlanningResult(
                    algorithm, FeasibilityStatus.NO_SAFE_ROUTE, null, null, null, null,
                    null, null, availableBattery, null, null, planningTimeMs, 0, route.failureReason());
        }

        EnergyEstimate estimate = missionEnergyEstimator.estimate(new EnergyEstimateRequest(
                route.distanceM(), homeWorldZ, route.requiredWorldZM(), true, false));
        return new AlgorithmPlanningResult(
                algorithm,
                FeasibilityStatus.FEASIBLE,
                route.distanceM(),
                route.requiredWorldZM(),
                estimate.plannedDurationSec(),
                estimate.cruiseSpeedMps(),
                estimate.estimatedEnergyMah(),
                estimate.estimatedBatteryUsedPercent(),
                availableBattery,
                estimate.safetyReservePercent(),
                estimate.requiredBatteryPercent(),
                planningTimeMs,
                route.points().size(),
                null);
    }

    private PlanningTradeoff tradeoff(AlgorithmPlanningResult shortest, AlgorithmPlanningResult aware) {
        Double energySaving = subtract(shortest.estimatedEnergyMah(), aware.estimatedEnergyMah());
        Double distanceDifference = subtract(aware.plannedDistanceM(), shortest.plannedDistanceM());
        Double durationDifference = subtract(aware.plannedDurationSec(), shortest.plannedDurationSec());
        return new PlanningTradeoff(
                energySaving,
                percentage(energySaving, shortest.estimatedEnergyMah()),
                distanceDifference,
                percentage(distanceDifference, shortest.plannedDistanceM()),
                durationDifference,
                percentage(durationDifference, shortest.plannedDurationSec()),
                subtract(aware.maxPlannedAltitudeM(), shortest.maxPlannedAltitudeM()),
                subtract(aware.planningTimeMs(), shortest.planningTimeMs()));
    }

    private SimulationPoint resolveTarget(String missionId, Order order) {
        if (order == null) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "Mission " + missionId + " has no order.");
        }
        Point point = order.getPoint();
        if (point == null) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "Mission " + missionId + " order has no target point.");
        }
        double x = point.getX();
        double y = point.getY();
        if (!Double.isFinite(x) || !Double.isFinite(y)) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "Mission order target point must contain finite coordinates.");
        }
        if (point.getSRID() == EPSG_4326_SRID && looksLikeLongitudeLatitude(x, y)) {
            throw new ApiException(ErrorCode.INVALID_REQUEST,
                    "Mission order target point is tagged as EPSG:4326 longitude/latitude; "
                            + "a local Gazebo simulation XY target is required for route planning.");
        }
        return new SimulationPoint(x, y);
    }

    private double resolveHomeWorldZ(SimulationPoint home, boolean validateBoundsAndRestriction) {
        EnvironmentSample sample = planningEnvironment.sample(home.x(), home.y());
        if (validateBoundsAndRestriction && !sample.insideWorldBounds()) {
            throw new IllegalArgumentException("Experiment home is outside the planning world.");
        }
        if (validateBoundsAndRestriction && sample.restricted()) {
            throw new IllegalArgumentException("Experiment home is inside a restricted zone.");
        }
        if (sample.surfaceElevationM() == null) {
            if (validateBoundsAndRestriction) {
                throw new IllegalArgumentException("Experiment home has no planning surface elevation.");
            }
            throw new ApiException(ErrorCode.INVALID_REQUEST, String.format(
                    "Home point has no planning surface elevation near x=%.2f, y=%.2f.", home.x(), home.y()));
        }
        return sample.surfaceElevationM();
    }

    private Optional<Double> resolveAvailableBatteryPercent(String missionId) {
        return missionDroneAssignmentRepository.findByMissionIdAndIsCurrentTrue(missionId)
                .map(MissionDroneAssignment::getDrone)
                .filter(drone -> drone.getDroneCode() != null && !drone.getDroneCode().isBlank())
                .flatMap(drone -> droneTelemetryRepository.findByDroneCode(drone.getDroneCode()))
                .map(DroneTelemetry::getBatteryPercent)
                .filter(this::isValidBatteryPercent);
    }

    private boolean looksLikeLongitudeLatitude(double x, double y) {
        return x >= -180.0 && x <= 180.0 && y >= -90.0 && y <= 90.0;
    }

    private boolean finite(double value) {
        return Double.isFinite(value);
    }

    private boolean isValidBatteryPercent(Double value) {
        return value != null && Double.isFinite(value) && value >= 0.0 && value <= 100.0;
    }

    private Double subtract(Double left, Double right) {
        return left == null || right == null ? null : left - right;
    }

    private Long subtract(Long left, Long right) {
        return left == null || right == null ? null : left - right;
    }

    private Double percentage(Double numerator, Double denominator) {
        if (numerator == null || denominator == null || denominator == 0.0) return null;
        double value = numerator / denominator * 100.0;
        return Double.isFinite(value) ? value : null;
    }

    private long elapsedMs(long started) {
        return Math.max(0L, (System.nanoTime() - started) / 1_000_000L);
    }
}
