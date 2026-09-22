package com.ondemandmonitoring.planning.service.impl;

import com.ondemandmonitoring.common.exception.ApiException;
import com.ondemandmonitoring.common.exception.ErrorCode;
import com.ondemandmonitoring.drone.domain.DroneTelemetry;
import com.ondemandmonitoring.drone.repository.DroneTelemetryRepository;
import com.ondemandmonitoring.mission.domain.Mission;
import com.ondemandmonitoring.mission.domain.MissionDroneAssignment;
import com.ondemandmonitoring.mission.domain.MissionPlan;
import com.ondemandmonitoring.mission.domain.PlanWaypoint;
import com.ondemandmonitoring.mission.enums.FeasibilityStatus;
import com.ondemandmonitoring.mission.enums.PlanningAlgorithm;
import com.ondemandmonitoring.mission.enums.WaypointReason;
import com.ondemandmonitoring.mission.repository.MissionDroneAssignmentRepository;
import com.ondemandmonitoring.mission.repository.MissionPlanRepository;
import com.ondemandmonitoring.mission.repository.MissionRepository;
import com.ondemandmonitoring.order.domain.Order;
import com.ondemandmonitoring.planning.dto.EnergyEstimate;
import com.ondemandmonitoring.planning.dto.EnergyEstimateRequest;
import com.ondemandmonitoring.planning.dto.PlannedRoute;
import com.ondemandmonitoring.planning.dto.SimulationPoint;
import com.ondemandmonitoring.planning.dto.response.EnvironmentSample;
import com.ondemandmonitoring.planning.service.MissionEnergyEstimator;
import com.ondemandmonitoring.planning.service.MissionPlanningService;
import com.ondemandmonitoring.planning.service.PlanningEnvironment;
import com.ondemandmonitoring.planning.service.RoutePlanner;
import com.ondemandmonitoring.planning.service.SimulationHomeProvider;
import java.util.Optional;
import java.time.Instant;
import org.locationtech.jts.geom.Point;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.transaction.annotation.Transactional;
import com.ondemandmonitoring.replanning.domain.ReplanningReason;

@Service
public class MissionPlanningServiceImpl implements MissionPlanningService {

    static final int LOCAL_SIMULATION_SRID = 0;
    static final int EPSG_4326_SRID = 4326;

    private final MissionRepository missionRepository;
    private final MissionPlanRepository missionPlanRepository;
    private final MissionDroneAssignmentRepository missionDroneAssignmentRepository;
    private final DroneTelemetryRepository droneTelemetryRepository;
    private final RoutePlanner directRoutePlanner;
    private final RoutePlanner aStarShortestRoutePlanner;
    private final RoutePlanner aStarEnergyAwareRoutePlanner;
    private final SimulationHomeProvider simulationHomeProvider;
    private final PlanningEnvironment planningEnvironment;
    private final MissionEnergyEstimator missionEnergyEstimator;

    public MissionPlanningServiceImpl(
            MissionRepository missionRepository,
            MissionPlanRepository missionPlanRepository,
            MissionDroneAssignmentRepository missionDroneAssignmentRepository,
            DroneTelemetryRepository droneTelemetryRepository,
            @Qualifier("directRoutePlanner") RoutePlanner directRoutePlanner,
            @Qualifier("aStarShortestRoutePlanner") RoutePlanner aStarShortestRoutePlanner,
            @Qualifier("aStarEnergyAwareRoutePlanner") RoutePlanner aStarEnergyAwareRoutePlanner,
            SimulationHomeProvider simulationHomeProvider,
            PlanningEnvironment planningEnvironment,
            MissionEnergyEstimator missionEnergyEstimator) {
        this.missionRepository = missionRepository;
        this.missionPlanRepository = missionPlanRepository;
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
    public PlannedRoute validateDirectRoute(String missionId) {
        PlanningContext context = resolvePlanningContext(missionId);
        return planRoute(context, directRoutePlanner);
    }

    @Override
    @Transactional
    public MissionPlan generateDirectPlan(String missionId) {
        return generatePlan(missionId, directRoutePlanner, PlanningAlgorithm.DIRECT);
    }

    @Override
    @Transactional
    public MissionPlan generateAStarShortestPlan(String missionId) {
        return generatePlan(missionId, aStarShortestRoutePlanner, PlanningAlgorithm.ASTAR_SHORTEST);
    }

    @Override
    @Transactional
    public MissionPlan generateAStarEnergyAwarePlan(String missionId) {
        return generatePlan(missionId, aStarEnergyAwareRoutePlanner, PlanningAlgorithm.ASTAR_ENERGY_AWARE);
    }

    @Override
    @Transactional
    public MissionPlan replanAStarEnergyAwareFromCurrentPosition(
            String missionId,
            double currentSimX,
            double currentSimY,
            ReplanningReason reason) {
        if (!Double.isFinite(currentSimX) || !Double.isFinite(currentSimY)) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "Replan start position must contain finite simX/simY.");
        }
        PlanningContext context = resolvePlanningContext(missionId);
        SimulationPoint currentPosition = new SimulationPoint(currentSimX, currentSimY);

        long startedAtNanos = System.nanoTime();
        PlannedRoute route = planRoute(currentPosition, context.target(), aStarEnergyAwareRoutePlanner);
        long planningTimeMs = Math.max(0L, (System.nanoTime() - startedAtNanos) / 1_000_000L);

        MissionPlan missionPlan = missionPlanRepository.findByMissionId(missionId)
                .orElseThrow(() -> new ApiException(ErrorCode.INVALID_REQUEST, "Mission has no active plan to replan: " + missionId));
        int nextVersion = Math.max(1, Optional.ofNullable(missionPlan.getPlanVersion()).orElse(1)) + 1;

        if (!route.feasible()) {
            missionPlan.setReplanningReason(reason.name());
            missionPlan.setReplanningStatus("NO_ROUTE_FOUND");
            missionPlan.setReplannedAt(Instant.now());
            return missionPlanRepository.save(missionPlan);
        }

        EnergyEstimate energyEstimate = missionEnergyEstimator.estimate(new EnergyEstimateRequest(
                route.distanceM(),
                resolveHomeWorldZ(currentPosition),
                route.requiredWorldZM(),
                true,
                false));
        BatterySnapshot batterySnapshot = resolveBatterySnapshot(context.mission().getId());
        FeasibilityStatus batteryFeasibility = resolveBatteryFeasibility(batterySnapshot, energyEstimate);
        if (batteryFeasibility != FeasibilityStatus.FEASIBLE) {
            missionPlan.setReplanningReason(reason.name());
            missionPlan.setReplanningStatus(batteryFeasibility.name());
            missionPlan.setReplannedAt(Instant.now());
            return missionPlanRepository.save(missionPlan);
        }

        missionPlan.setMission(context.mission());
        clearPlanningResult(missionPlan);
        missionPlan.setPlanningAlgorithm(PlanningAlgorithm.ASTAR_ENERGY_AWARE);
        missionPlan.setPlanVersion(nextVersion);
        missionPlan.setReplanningReason(reason.name());
        missionPlan.setReplanningStatus("BACKEND_PLAN_APPLIED");
        missionPlan.setReplannedAt(Instant.now());
        missionPlan.setPlannedDistanceM(route.distanceM());
        missionPlan.setMaxPlannedAltitudeM(route.requiredWorldZM());
        missionPlan.setFeasibilityStatus(route.feasible()
                ? FeasibilityStatus.FEASIBLE
                : FeasibilityStatus.NO_SAFE_ROUTE);
        missionPlan.setPlanningTimeMs(planningTimeMs);

        applyEnergyEstimate(missionPlan, energyEstimate, context.mission().getId());

        for (int sequence = 0; sequence < route.points().size(); sequence++) {
            missionPlan.getWaypoints().add(toWaypoint(missionPlan, sequence, route.points().get(sequence), route.points().size()));
        }

        return missionPlanRepository.save(missionPlan);
    }

    private MissionPlan generatePlan(
            String missionId,
            RoutePlanner routePlanner,
            PlanningAlgorithm planningAlgorithm) {
        PlanningContext context = resolvePlanningContext(missionId);

        long startedAtNanos = System.nanoTime();
        PlannedRoute route = planRoute(context, routePlanner);
        long planningTimeMs = Math.max(0L, (System.nanoTime() - startedAtNanos) / 1_000_000L);

        MissionPlan missionPlan = missionPlanRepository.findByMissionId(missionId)
                .orElseGet(() -> {
                    MissionPlan newPlan = new MissionPlan();
                    newPlan.setMission(context.mission());
                    return newPlan;
                });

        missionPlan.setMission(context.mission());
        clearPlanningResult(missionPlan);
        missionPlan.setPlanningAlgorithm(planningAlgorithm);
        if (missionPlan.getPlanVersion() == null) {
            missionPlan.setPlanVersion(1);
        }
        missionPlan.setReplanningReason(null);
        missionPlan.setReplanningStatus(null);
        missionPlan.setReplannedAt(null);
        missionPlan.setPlannedDistanceM(route.distanceM());
        missionPlan.setMaxPlannedAltitudeM(route.requiredWorldZM());
        missionPlan.setFeasibilityStatus(route.feasible()
                ? FeasibilityStatus.FEASIBLE
                : FeasibilityStatus.NO_SAFE_ROUTE);
        missionPlan.setPlanningTimeMs(planningTimeMs);

        if (route.feasible()) {
            EnergyEstimate energyEstimate = missionEnergyEstimator.estimate(new EnergyEstimateRequest(
                    route.distanceM(),
                    resolveHomeWorldZ(context.home()),
                    route.requiredWorldZM(),
                    true,
                    false));
            applyEnergyEstimate(missionPlan, energyEstimate, context.mission().getId());

            for (int sequence = 0; sequence < route.points().size(); sequence++) {
                missionPlan.getWaypoints().add(toWaypoint(missionPlan, sequence, route.points().get(sequence), route.points().size()));
            }
        }

        return missionPlanRepository.save(missionPlan);
    }

    private void clearPlanningResult(MissionPlan missionPlan) {
        if (!missionPlan.getWaypoints().isEmpty()) {
            missionPlan.getWaypoints().clear();
            missionPlanRepository.flush();
        }
        missionPlan.setPlannedDistanceM(null);
        missionPlan.setMaxPlannedAltitudeM(null);
        missionPlan.setPlanningTimeMs(null);
        missionPlan.setFeasibilityStatus(null);
        clearEnergyFields(missionPlan);
    }

    private void clearEnergyFields(MissionPlan missionPlan) {
        missionPlan.setPlannedDurationSec(null);
        missionPlan.setPlannedCruiseSpeedMps(null);
        missionPlan.setEstimatedEnergyMah(null);
        missionPlan.setEstimatedBatteryUsedPercent(null);
        missionPlan.setBatteryCapacityMah(null);
        missionPlan.setAvailableBatteryPercentAtPlanning(null);
        missionPlan.setSafetyReservePercent(null);
        missionPlan.setRequiredBatteryPercent(null);
    }

    private void applyEnergyEstimate(
            MissionPlan missionPlan,
            EnergyEstimate energyEstimate,
            String missionId) {
        missionPlan.setPlannedDurationSec(energyEstimate.plannedDurationSec());
        missionPlan.setPlannedCruiseSpeedMps(energyEstimate.cruiseSpeedMps());
        missionPlan.setEstimatedEnergyMah(energyEstimate.estimatedEnergyMah());
        missionPlan.setEstimatedBatteryUsedPercent(energyEstimate.estimatedBatteryUsedPercent());
        missionPlan.setBatteryCapacityMah(energyEstimate.batteryCapacityMah());
        BatterySnapshot batterySnapshot = resolveBatterySnapshot(missionId);
        missionPlan.setAvailableBatteryPercentAtPlanning(batterySnapshot.batteryPercent().orElse(null));
        missionPlan.setSafetyReservePercent(energyEstimate.safetyReservePercent());
        missionPlan.setRequiredBatteryPercent(energyEstimate.requiredBatteryPercent());
        missionPlan.setFeasibilityStatus(resolveBatteryFeasibility(batterySnapshot, energyEstimate));
    }

    private FeasibilityStatus resolveBatteryFeasibility(
            BatterySnapshot batterySnapshot,
            EnergyEstimate energyEstimate) {
        if (!batterySnapshot.hasAssignedDrone()) {
            return FeasibilityStatus.FEASIBLE;
        }
        if (batterySnapshot.batteryPercent().isEmpty()) {
            return FeasibilityStatus.BATTERY_DATA_UNAVAILABLE;
        }

        double currentBatteryPercent = batterySnapshot.batteryPercent().get();
        double estimatedRemainingPercent = currentBatteryPercent - energyEstimate.estimatedBatteryUsedPercent();
        if (estimatedRemainingPercent + 1.0e-9 < energyEstimate.safetyReservePercent()) {
            return FeasibilityStatus.INSUFFICIENT_BATTERY;
        }
        return FeasibilityStatus.FEASIBLE;
    }

    private double resolveHomeWorldZ(SimulationPoint home) {
        EnvironmentSample homeSample = planningEnvironment.sample(home.x(), home.y());
        if (homeSample.surfaceElevationM() == null) {
            throw new ApiException(
                    ErrorCode.INVALID_REQUEST,
                    String.format("Home point has no planning surface elevation near x=%.2f, y=%.2f.", home.x(), home.y()));
        }
        return homeSample.surfaceElevationM();
    }

    private BatterySnapshot resolveBatterySnapshot(String missionId) {
        if (missionId == null) {
            return new BatterySnapshot(false, Optional.empty());
        }
        Optional<MissionDroneAssignment> assignment =
                missionDroneAssignmentRepository.findByMissionIdAndIsCurrentTrue(missionId);
        if (assignment.isEmpty()) {
            return new BatterySnapshot(false, Optional.empty());
        }

        Optional<Double> batteryPercent = assignment
                .map(MissionDroneAssignment::getDrone)
                .filter(drone -> drone.getDroneCode() != null && !drone.getDroneCode().isBlank())
                .flatMap(drone -> droneTelemetryRepository.findByDroneCode(drone.getDroneCode()))
                .map(DroneTelemetry::getBatteryPercent)
                .filter(this::isValidBatteryPercent);
        return new BatterySnapshot(true, batteryPercent);
    }

    private boolean isValidBatteryPercent(Double value) {
        return value != null && Double.isFinite(value) && value >= 0.0 && value <= 100.0;
    }

    private PlanningContext resolvePlanningContext(String missionId) {
        Mission mission = missionRepository.findByIdWithOrder(missionId)
                .orElseThrow(() -> new ApiException(ErrorCode.MISSION_NOT_FOUND, "Mission not found: " + missionId));

        Order order = mission.getOrder();
        if (order == null) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "Mission " + missionId + " has no order.");
        }

        Point targetPoint = order.getPoint();
        if (targetPoint == null) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "Mission " + missionId + " order has no target point.");
        }

        return new PlanningContext(
                mission,
                simulationHomeProvider.home(),
                resolveSimulationTarget(targetPoint));
    }

    private PlannedRoute planRoute(PlanningContext context, RoutePlanner routePlanner) {
        return routePlanner.plan(
                context.home().x(),
                context.home().y(),
                context.target().x(),
                context.target().y());
    }

    private PlannedRoute planRoute(
            SimulationPoint start,
            SimulationPoint target,
            RoutePlanner routePlanner) {
        return routePlanner.plan(
                start.x(),
                start.y(),
                target.x(),
                target.y());
    }

    private PlanWaypoint toWaypoint(
            MissionPlan missionPlan,
            int sequence,
            PlannedRoute.RoutePoint routePoint,
            int routePointCount) {
        PlanWaypoint waypoint = new PlanWaypoint();
        waypoint.setMissionPlan(missionPlan);
        waypoint.setSequence(sequence);
        waypoint.setSimX(routePoint.simX());
        waypoint.setSimY(routePoint.simY());
        waypoint.setAltitudeM(routePoint.worldZM());
        waypoint.setPlannedSpeedMps(null);
        waypoint.setReason(sequence == 0
                ? WaypointReason.START
                : sequence == routePointCount - 1
                        ? WaypointReason.TARGET
                        : WaypointReason.CRUISE);
        return waypoint;
    }

    private SimulationPoint resolveSimulationTarget(Point point) {
        double targetX = point.getX();
        double targetY = point.getY();

        if (!Double.isFinite(targetX) || !Double.isFinite(targetY)) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "Mission order target point must contain finite coordinates.");
        }

        if (point.getSRID() == EPSG_4326_SRID && looksLikeLongitudeLatitude(targetX, targetY)) {
            throw new ApiException(
                    ErrorCode.INVALID_REQUEST,
                    "Mission order target point is tagged as EPSG:4326 longitude/latitude; "
                            + "a local Gazebo simulation XY target is required for route planning.");
        }

        return new SimulationPoint(targetX, targetY);
    }

    private boolean looksLikeLongitudeLatitude(double x, double y) {
        return x >= -180.0 && x <= 180.0 && y >= -90.0 && y <= 90.0;
    }

    private record PlanningContext(
            Mission mission,
            SimulationPoint home,
            SimulationPoint target) {
    }

    private record BatterySnapshot(
            boolean hasAssignedDrone,
            Optional<Double> batteryPercent) {
    }
}
