package com.ondemandmonitoring.planning.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.ondemandmonitoring.mission.domain.Mission;
import com.ondemandmonitoring.mission.domain.MissionPlan;
import com.ondemandmonitoring.mission.domain.PlanWaypoint;
import com.ondemandmonitoring.mission.enums.FeasibilityStatus;
import com.ondemandmonitoring.mission.enums.MissionStatus;
import com.ondemandmonitoring.mission.enums.PlanningAlgorithm;
import com.ondemandmonitoring.mission.repository.MissionPlanRepository;
import com.ondemandmonitoring.mission.repository.MissionRepository;
import com.ondemandmonitoring.order.domain.Order;
import com.ondemandmonitoring.order.repository.OrderRepository;
import com.ondemandmonitoring.planning.dto.AlgorithmPlanningResult;
import com.ondemandmonitoring.planning.dto.PlanningComparisonResult;
import com.ondemandmonitoring.planning.dto.PlanningTradeoff;
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
class PlanningComparisonE2ETest {

    private static final double TOLERANCE = 1.0e-9;
    private static final GeometryFactory GEOMETRY = new GeometryFactory(new PrecisionModel(), 4326);

    @Autowired private PlanningComparisonService comparisonService;
    @Autowired private MissionPlanningService missionPlanningService;
    @Autowired private MissionRepository missionRepository;
    @Autowired private MissionPlanRepository missionPlanRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private EntityManager entityManager;
    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    void comparesRealScenariosWithoutMutatingOperationalMissionPlan() {
        Order source = orderRepository.findAll().stream().findFirst().orElseThrow();
        Order order = orderRepository.saveAndFlush(copyOrder(source, point(200.0, -280.0)));
        Mission mission = new Mission();
        mission.setMissionCode("E2E_PLANNING_COMPARISON_" + UUID.randomUUID().toString().substring(0, 8));
        mission.setStatus(MissionStatus.CREATED);
        mission.setOrder(order);
        mission = missionRepository.saveAndFlush(mission);

        missionPlanningService.generateDirectPlan(mission.getId());
        entityManager.flush();
        entityManager.clear();
        PlanSnapshot before = snapshot(missionPlanRepository.findByMissionId(mission.getId()).orElseThrow());
        long plansBefore = countPlans(mission.getId());
        long waypointsBefore = countWaypoints(mission.getId());

        PlanningComparisonResult terrain = comparisonService.compare(mission.getId());
        assertContext(terrain, mission.getId(), 200.0, -280.0);
        AlgorithmPlanningResult direct = result(terrain, PlanningAlgorithm.DIRECT);
        AlgorithmPlanningResult shortest = result(terrain, PlanningAlgorithm.ASTAR_SHORTEST);
        AlgorithmPlanningResult aware = result(terrain, PlanningAlgorithm.ASTAR_ENERGY_AWARE);
        assertThat(terrain.results()).hasSize(3);
        assertThat(terrain.results()).allMatch(value -> value.feasibilityStatus() == FeasibilityStatus.FEASIBLE);
        assertThat(aware.plannedDistanceM()).isGreaterThan(shortest.plannedDistanceM());
        assertThat(aware.maxPlannedAltitudeM()).isLessThan(shortest.maxPlannedAltitudeM());
        assertThat(aware.estimatedEnergyMah()).isLessThan(shortest.estimatedEnergyMah());
        assertTradeoff(terrain.energyAwareVsShortest(), shortest, aware);
        print("TERRAIN", terrain);

        order = orderRepository.findById(order.getId()).orElseThrow();
        order.setPoint(point(-400.0, -280.0));
        orderRepository.saveAndFlush(order);
        PlanningComparisonResult detour = comparisonService.compare(mission.getId());
        assertThat(result(detour, PlanningAlgorithm.DIRECT).feasibilityStatus())
                .isEqualTo(FeasibilityStatus.NO_SAFE_ROUTE);
        assertThat(result(detour, PlanningAlgorithm.DIRECT).estimatedEnergyMah()).isNull();
        assertThat(result(detour, PlanningAlgorithm.ASTAR_SHORTEST).feasibilityStatus())
                .isEqualTo(FeasibilityStatus.FEASIBLE);
        assertThat(result(detour, PlanningAlgorithm.ASTAR_ENERGY_AWARE).feasibilityStatus())
                .isEqualTo(FeasibilityStatus.FEASIBLE);
        print("DETOUR", detour);

        order = orderRepository.findById(order.getId()).orElseThrow();
        order.setPoint(point(-200.0, -280.0));
        orderRepository.saveAndFlush(order);
        PlanningComparisonResult restricted = comparisonService.compare(mission.getId());
        assertThat(restricted.results()).allMatch(value ->
                value.feasibilityStatus() == FeasibilityStatus.NO_SAFE_ROUTE
                        && value.estimatedEnergyMah() == null
                        && value.waypointCount() == 0);
        assertThat(restricted.energyAwareVsShortest().energySavingMah()).isNull();
        print("RESTRICTED", restricted);

        entityManager.flush();
        entityManager.clear();
        PlanSnapshot after = snapshot(missionPlanRepository.findByMissionId(mission.getId()).orElseThrow());
        assertThat(after).isEqualTo(before);
        assertThat(countPlans(mission.getId())).isEqualTo(plansBefore).isEqualTo(1);
        assertThat(countWaypoints(mission.getId())).isEqualTo(waypointsBefore);
        System.out.printf("COMPARISON_E2E|READ_ONLY|plan=%s|algorithm=%s|plansBefore=%d|plansAfter=%d|waypointsBefore=%d|waypointsAfter=%d|unchanged=%s%n",
                after.id(), after.algorithm(), plansBefore, countPlans(mission.getId()),
                waypointsBefore, countWaypoints(mission.getId()), after.equals(before));
    }

    private void assertContext(PlanningComparisonResult result, String missionId, double targetX, double targetY) {
        assertThat(result.missionId()).isEqualTo(missionId);
        assertThat(result.homeX()).isEqualTo(0.0);
        assertThat(result.homeY()).isEqualTo(-280.0);
        assertThat(result.targetX()).isEqualTo(targetX);
        assertThat(result.targetY()).isEqualTo(targetY);
        assertThat(result.homeWorldZ()).isEqualTo(9.4);
    }

    private void assertTradeoff(PlanningTradeoff tradeoff, AlgorithmPlanningResult shortest,
            AlgorithmPlanningResult aware) {
        double energySaving = shortest.estimatedEnergyMah() - aware.estimatedEnergyMah();
        double distanceDifference = aware.plannedDistanceM() - shortest.plannedDistanceM();
        double durationDifference = aware.plannedDurationSec() - shortest.plannedDurationSec();
        assertThat(tradeoff.energySavingMah()).isCloseTo(energySaving, offset());
        assertThat(tradeoff.energySavingPercent())
                .isCloseTo(energySaving / shortest.estimatedEnergyMah() * 100.0, offset());
        assertThat(tradeoff.distanceDifferenceM()).isCloseTo(distanceDifference, offset());
        assertThat(tradeoff.distanceDifferencePercent())
                .isCloseTo(distanceDifference / shortest.plannedDistanceM() * 100.0, offset());
        assertThat(tradeoff.durationDifferenceSec()).isCloseTo(durationDifference, offset());
        assertThat(tradeoff.altitudeDifferenceM()).isCloseTo(
                aware.maxPlannedAltitudeM() - shortest.maxPlannedAltitudeM(), offset());
    }

    private AlgorithmPlanningResult result(PlanningComparisonResult comparison, PlanningAlgorithm algorithm) {
        return comparison.results().stream().filter(value -> value.algorithm() == algorithm).findFirst().orElseThrow();
    }

    private PlanSnapshot snapshot(MissionPlan plan) {
        List<WaypointSnapshot> waypoints = plan.getWaypoints().stream()
                .sorted(Comparator.comparingInt(PlanWaypoint::getSequence))
                .map(value -> new WaypointSnapshot(value.getId(), value.getSequence(), value.getReason(),
                        value.getSimX(), value.getSimY(), value.getAltitudeM(), value.getPlannedSpeedMps()))
                .toList();
        return new PlanSnapshot(plan.getId(), plan.getPlanningAlgorithm(), plan.getFeasibilityStatus(),
                plan.getPlannedDistanceM(), plan.getMaxPlannedAltitudeM(), plan.getPlannedDurationSec(),
                plan.getEstimatedEnergyMah(), plan.getEstimatedBatteryUsedPercent(),
                plan.getRequiredBatteryPercent(), plan.getPlanningTimeMs(), waypoints);
    }

    private void print(String scenario, PlanningComparisonResult comparison) {
        for (AlgorithmPlanningResult value : comparison.results()) {
            System.out.printf("COMPARISON_E2E|%s|algorithm=%s|status=%s|distance=%s|worldZ=%s|duration=%s|energy=%s|battery=%s|required=%s|planningMs=%d|waypoints=%d|reason=%s%n",
                    scenario, value.algorithm(), value.feasibilityStatus(), value.plannedDistanceM(),
                    value.maxPlannedAltitudeM(), value.plannedDurationSec(), value.estimatedEnergyMah(),
                    value.estimatedBatteryUsedPercent(), value.requiredBatteryPercent(), value.planningTimeMs(),
                    value.waypointCount(), value.failureReason());
        }
        PlanningTradeoff tradeoff = comparison.energyAwareVsShortest();
        System.out.printf("COMPARISON_E2E|%s|TRADEOFF|energySaving=%s|energyPercent=%s|distanceDifference=%s|distancePercent=%s|durationDifference=%s|durationPercent=%s|altitudeDifference=%s|planningDifference=%s|totalMs=%d%n",
                scenario, tradeoff.energySavingMah(), tradeoff.energySavingPercent(),
                tradeoff.distanceDifferenceM(), tradeoff.distanceDifferencePercent(),
                tradeoff.durationDifferenceSec(), tradeoff.durationDifferencePercent(),
                tradeoff.altitudeDifferenceM(), tradeoff.planningTimeDifferenceMs(),
                comparison.totalComparisonTimeMs());
    }

    private Order copyOrder(Order source, Point target) {
        Order order = new Order();
        order.setCustomer(source.getCustomer());
        order.setTitle("E2E_PLANNING_COMPARISON_ORDER");
        order.setPurpose(source.getPurpose());
        order.setService(source.getService());
        order.setDescription("Temporary transactional planning comparison record");
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
        Point point = GEOMETRY.createPoint(new Coordinate(x, y));
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

    private record PlanSnapshot(String id, PlanningAlgorithm algorithm, FeasibilityStatus status,
            Double distance, Double worldZ, Double duration, Double energy, Double battery,
            Double required, Long planningMs, List<WaypointSnapshot> waypoints) {
    }

    private record WaypointSnapshot(String id, int sequence, Object reason, double x, double y,
            double worldZ, Double speed) {
    }
}
