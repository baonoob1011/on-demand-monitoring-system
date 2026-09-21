package com.ondemandmonitoring.planning.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.ondemandmonitoring.mission.domain.Mission;
import com.ondemandmonitoring.mission.domain.MissionPlan;
import com.ondemandmonitoring.mission.domain.PlanWaypoint;
import com.ondemandmonitoring.mission.enums.FeasibilityStatus;
import com.ondemandmonitoring.mission.enums.MissionStatus;
import com.ondemandmonitoring.mission.enums.PlanningAlgorithm;
import com.ondemandmonitoring.mission.enums.WaypointReason;
import com.ondemandmonitoring.mission.repository.MissionPlanRepository;
import com.ondemandmonitoring.mission.repository.MissionRepository;
import com.ondemandmonitoring.order.domain.Order;
import com.ondemandmonitoring.order.repository.OrderRepository;
import com.ondemandmonitoring.planning.config.PlanningEnergyProperties;
import com.ondemandmonitoring.planning.dto.SimulationPoint;
import com.ondemandmonitoring.planning.dto.response.EnvironmentSample;
import jakarta.persistence.EntityManager;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
@ActiveProfiles("e2e")
class AStarEnergyAwarePlanningE2ETest {

    private static final double TOLERANCE = 1.0e-9;
    private static final GeometryFactory GEOMETRY_FACTORY =
            new GeometryFactory(new PrecisionModel(), 4326);

    @Autowired private MissionPlanningService missionPlanningService;
    @Autowired private MissionRepository missionRepository;
    @Autowired private MissionPlanRepository missionPlanRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private SimulationHomeProvider simulationHomeProvider;
    @Autowired private PlanningEnvironment planningEnvironment;
    @Autowired private PlanningEnergyProperties energy;
    @Autowired private EntityManager entityManager;
    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    void persistsEnergyAwarePlanAndReusesItAcrossAlgorithmsAndOutcomes() {
        Order source = orderRepository.findAll().stream().findFirst().orElseThrow();
        Order order = orderRepository.saveAndFlush(copyOrder(source, point(200.0, -280.0)));
        Mission mission = new Mission();
        mission.setMissionCode("E2E_ASTAR_ENERGY_AWARE_" + UUID.randomUUID().toString().substring(0, 8));
        mission.setStatus(MissionStatus.CREATED);
        mission.setOrder(order);
        mission = missionRepository.saveAndFlush(mission);

        PlanMetrics direct = metrics(missionPlanningService.generateDirectPlan(mission.getId()));
        String planId = direct.planId();
        entityManager.flush();
        PlanMetrics shortest = metrics(missionPlanningService.generateAStarShortestPlan(mission.getId()));
        entityManager.flush();
        MissionPlan energyAwareResult = missionPlanningService.generateAStarEnergyAwarePlan(mission.getId());
        entityManager.flush();
        entityManager.clear();

        MissionPlan persisted = missionPlanRepository.findByMissionId(mission.getId()).orElseThrow();
        PlanMetrics aware = metrics(persisted);
        List<PlanWaypoint> waypoints = sortedWaypoints(persisted);

        assertThat(direct.planId()).isEqualTo(planId);
        assertThat(shortest.planId()).isEqualTo(planId);
        assertThat(energyAwareResult.getId()).isEqualTo(planId);
        assertThat(aware.planId()).isEqualTo(planId);
        assertThat(persisted.getPlanningAlgorithm()).isEqualTo(PlanningAlgorithm.ASTAR_ENERGY_AWARE);
        assertThat(persisted.getFeasibilityStatus()).isEqualTo(FeasibilityStatus.FEASIBLE);
        assertThat(countPlans(mission.getId())).isEqualTo(1);
        assertThat(aware.distanceM()).isGreaterThan(shortest.distanceM());
        assertThat(aware.worldZM()).isLessThan(shortest.worldZM());
        assertThat(aware.energyMah()).isLessThan(shortest.energyMah());
        assertThat(aware.availableBatteryPercent()).isNull();
        assertWaypoints(waypoints, persisted.getMaxPlannedAltitudeM());
        assertThat(waypoints.size()).isLessThan(100);

        SimulationPoint home = simulationHomeProvider.home();
        EnvironmentSample homeSample = planningEnvironment.sample(home.x(), home.y());
        double climbM = Math.max(0.0, aware.worldZM() - homeSample.surfaceElevationM());
        double cruiseSec = aware.distanceM() / energy.cruiseSpeedMps();
        double ascendSec = climbM / energy.ascendSpeedMps();
        double expectedDuration = cruiseSec + ascendSec;
        double expectedEnergy = energy.cruiseCurrentA() * 1000.0 * cruiseSec / 3600.0
                + energy.ascendCurrentA() * 1000.0 * ascendSec / 3600.0;
        double expectedBattery = expectedEnergy / energy.batteryCapacityMah() * 100.0;
        assertThat(aware.durationSec()).isCloseTo(expectedDuration, offset());
        assertThat(aware.energyMah()).isCloseTo(expectedEnergy, offset());
        assertThat(aware.batteryUsedPercent()).isCloseTo(expectedBattery, offset());
        assertThat(aware.requiredBatteryPercent())
                .isCloseTo(expectedBattery + energy.safetyReservePercent(), offset());

        printComparison(direct, shortest, aware, homeSample.surfaceElevationM(), expectedEnergy);
        printWaypoints("MAIN", waypoints);

        order = orderRepository.findById(order.getId()).orElseThrow();
        order.setPoint(point(-400.0, -280.0));
        orderRepository.saveAndFlush(order);
        MissionPlan airportResult = missionPlanningService.generateAStarEnergyAwarePlan(mission.getId());
        entityManager.flush();
        entityManager.clear();
        MissionPlan airport = missionPlanRepository.findByMissionId(mission.getId()).orElseThrow();
        List<PlanWaypoint> airportWaypoints = sortedWaypoints(airport);
        assertThat(airportResult.getId()).isEqualTo(planId);
        assertThat(airport.getId()).isEqualTo(planId);
        assertThat(airport.getPlanningAlgorithm()).isEqualTo(PlanningAlgorithm.ASTAR_ENERGY_AWARE);
        assertThat(airport.getFeasibilityStatus()).isEqualTo(FeasibilityStatus.FEASIBLE);
        assertThat(countPlans(mission.getId())).isEqualTo(1);
        assertWaypoints(airportWaypoints, airport.getMaxPlannedAltitudeM());
        assertSegmentsAvoidRestrictions(airportWaypoints);
        System.out.printf("ENERGY_MISSION_E2E|AIRPORT|plan=%s|distance=%.12f|worldZ=%.12f|duration=%.12f|energy=%.12f|battery=%.12f|required=%.12f|planningMs=%d|waypoints=%d%n",
                planId, airport.getPlannedDistanceM(), airport.getMaxPlannedAltitudeM(),
                airport.getPlannedDurationSec(), airport.getEstimatedEnergyMah(),
                airport.getEstimatedBatteryUsedPercent(), airport.getRequiredBatteryPercent(),
                airport.getPlanningTimeMs(), airportWaypoints.size());

        order = orderRepository.findById(order.getId()).orElseThrow();
        order.setPoint(point(-200.0, -280.0));
        orderRepository.saveAndFlush(order);
        MissionPlan infeasibleResult = missionPlanningService.generateAStarEnergyAwarePlan(mission.getId());
        entityManager.flush();
        entityManager.clear();
        MissionPlan infeasible = missionPlanRepository.findByMissionId(mission.getId()).orElseThrow();
        assertThat(infeasibleResult.getId()).isEqualTo(planId);
        assertThat(infeasible.getId()).isEqualTo(planId);
        assertThat(infeasible.getPlanningAlgorithm()).isEqualTo(PlanningAlgorithm.ASTAR_ENERGY_AWARE);
        assertThat(infeasible.getFeasibilityStatus()).isEqualTo(FeasibilityStatus.NO_SAFE_ROUTE);
        assertThat(infeasible.getWaypoints()).isEmpty();
        assertThat(infeasible.getPlannedDurationSec()).isNull();
        assertThat(infeasible.getPlannedCruiseSpeedMps()).isNull();
        assertThat(infeasible.getEstimatedEnergyMah()).isNull();
        assertThat(infeasible.getEstimatedBatteryUsedPercent()).isNull();
        assertThat(infeasible.getAvailableBatteryPercentAtPlanning()).isNull();
        assertThat(infeasible.getSafetyReservePercent()).isNull();
        assertThat(infeasible.getRequiredBatteryPercent()).isNull();
        assertThat(countPlans(mission.getId())).isEqualTo(1);
        assertThat(countWaypoints(mission.getId())).isZero();
        System.out.printf("ENERGY_MISSION_E2E|INFEASIBLE|plan=%s|status=%s|planningMs=%d|waypoints=%d|energy=%s%n",
                planId, infeasible.getFeasibilityStatus(), infeasible.getPlanningTimeMs(),
                infeasible.getWaypoints().size(), String.valueOf(infeasible.getEstimatedEnergyMah()));
    }

    private void assertSegmentsAvoidRestrictions(List<PlanWaypoint> waypoints) {
        PlanningEnvironment snapshot = planningEnvironment.snapshot();
        for (int i = 1; i < waypoints.size(); i++) {
            PlanWaypoint from = waypoints.get(i - 1);
            PlanWaypoint to = waypoints.get(i);
            int steps = Math.max(1, (int) Math.ceil(Math.hypot(to.getSimX() - from.getSimX(), to.getSimY() - from.getSimY())));
            for (int step = 0; step <= steps; step++) {
                double fraction = step / (double) steps;
                EnvironmentSample sample = snapshot.sample(
                        from.getSimX() + (to.getSimX() - from.getSimX()) * fraction,
                        from.getSimY() + (to.getSimY() - from.getSimY()) * fraction);
                assertThat(sample.insideWorldBounds()).isTrue();
                assertThat(sample.restricted()).isFalse();
                assertThat(sample.surfaceElevationM()).isNotNull();
            }
        }
    }

    private void assertWaypoints(List<PlanWaypoint> waypoints, double worldZ) {
        assertThat(waypoints).isNotEmpty();
        assertThat(waypoints.getFirst().getReason()).isEqualTo(WaypointReason.START);
        assertThat(waypoints.getLast().getReason()).isEqualTo(WaypointReason.TARGET);
        for (int i = 0; i < waypoints.size(); i++) {
            assertThat(waypoints.get(i).getSequence()).isEqualTo(i);
            assertThat(waypoints.get(i).getAltitudeM()).isCloseTo(worldZ, offset());
            assertThat(waypoints.get(i).getPlannedSpeedMps()).isNull();
            if (i > 0 && i < waypoints.size() - 1) {
                assertThat(waypoints.get(i).getReason()).isEqualTo(WaypointReason.CRUISE);
            }
        }
    }

    private void printComparison(PlanMetrics direct, PlanMetrics shortest, PlanMetrics aware,
            double homeWorldZ, double expectedEnergy) {
        System.out.printf("ENERGY_MISSION_E2E|HOME|worldZ=%.12f%n", homeWorldZ);
        printMetrics("DIRECT", direct);
        printMetrics("ASTAR_SHORTEST", shortest);
        printMetrics("ASTAR_ENERGY_AWARE", aware);
        double energySaving = shortest.energyMah() - aware.energyMah();
        double distanceDifference = aware.distanceM() - shortest.distanceM();
        System.out.printf("ENERGY_MISSION_E2E|TRADEOFF|distanceDifference=%.12f|distancePercent=%.12f|worldZDifference=%.12f|energySaving=%.12f|energySavingPercent=%.12f|durationDifference=%.12f|expectedEnergy=%.12f|persistedEnergy=%.12f|difference=%.12g%n",
                distanceDifference, distanceDifference / shortest.distanceM() * 100.0,
                aware.worldZM() - shortest.worldZM(), energySaving,
                energySaving / shortest.energyMah() * 100.0,
                aware.durationSec() - shortest.durationSec(), expectedEnergy, aware.energyMah(),
                aware.energyMah() - expectedEnergy);
    }

    private void printMetrics(String algorithm, PlanMetrics value) {
        System.out.printf("ENERGY_MISSION_E2E|%s|plan=%s|distance=%.12f|worldZ=%.12f|duration=%.12f|energy=%.12f|battery=%.12f|required=%.12f|available=%s|planningMs=%d|waypoints=%d%n",
                algorithm, value.planId(), value.distanceM(), value.worldZM(), value.durationSec(),
                value.energyMah(), value.batteryUsedPercent(), value.requiredBatteryPercent(),
                String.valueOf(value.availableBatteryPercent()), value.planningTimeMs(), value.waypointCount());
    }

    private void printWaypoints(String label, List<PlanWaypoint> waypoints) {
        for (PlanWaypoint waypoint : waypoints) {
            System.out.printf("ENERGY_MISSION_E2E|WP|case=%s|sequence=%d|reason=%s|x=%.3f|y=%.3f|z=%.3f|speed=%s%n",
                    label, waypoint.getSequence(), waypoint.getReason(), waypoint.getSimX(), waypoint.getSimY(),
                    waypoint.getAltitudeM(), String.valueOf(waypoint.getPlannedSpeedMps()));
        }
    }

    private PlanMetrics metrics(MissionPlan plan) {
        return new PlanMetrics(plan.getId(), plan.getPlannedDistanceM(), plan.getMaxPlannedAltitudeM(),
                plan.getPlannedDurationSec(), plan.getEstimatedEnergyMah(), plan.getEstimatedBatteryUsedPercent(),
                plan.getRequiredBatteryPercent(), plan.getAvailableBatteryPercentAtPlanning(),
                plan.getPlanningTimeMs(), plan.getWaypoints().size());
    }

    private List<PlanWaypoint> sortedWaypoints(MissionPlan plan) {
        return plan.getWaypoints().stream().sorted(Comparator.comparingInt(PlanWaypoint::getSequence)).toList();
    }

    private Order copyOrder(Order source, Point target) {
        Order order = new Order();
        order.setCustomer(source.getCustomer());
        order.setTitle("E2E_ASTAR_ENERGY_AWARE_ORDER");
        order.setPurpose(source.getPurpose());
        order.setService(source.getService());
        order.setDescription("Temporary transactional ASTAR_ENERGY_AWARE E2E record");
        order.setAddress("LOCAL_SIMULATION_METERS_GAZEBO_XY");
        order.setPoint(target);
        order.setPreferredDate(source.getPreferredDate());
        order.setPreferredTime(source.getPreferredTime());
        order.setMediaType(source.getMediaType());
        order.setDurationOfVideo(source.getDurationOfVideo());
        order.setNumberOfPhoto(source.getNumberOfPhoto());
        order.setOrderStatus(source.getOrderStatus());
        return order;
    }

    private Point point(double x, double y) {
        Point point = GEOMETRY_FACTORY.createPoint(new Coordinate(x, y));
        point.setSRID(4326);
        return point;
    }

    private long countPlans(String missionId) {
        return jdbcTemplate.queryForObject(
                "select count(*) from mission_plans where mission_id = ?", Long.class, missionId);
    }

    private long countWaypoints(String missionId) {
        return jdbcTemplate.queryForObject("""
                select count(*) from plan_waypoints pw
                join mission_plans mp on mp.id = pw.mission_plan_id
                where mp.mission_id = ?
                """, Long.class, missionId);
    }

    private org.assertj.core.data.Offset<Double> offset() {
        return org.assertj.core.data.Offset.offset(TOLERANCE);
    }

    private record PlanMetrics(
            String planId,
            double distanceM,
            double worldZM,
            double durationSec,
            double energyMah,
            double batteryUsedPercent,
            double requiredBatteryPercent,
            Double availableBatteryPercent,
            long planningTimeMs,
            int waypointCount) {
    }
}
