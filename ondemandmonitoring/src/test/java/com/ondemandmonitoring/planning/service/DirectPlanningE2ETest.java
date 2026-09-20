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
import com.ondemandmonitoring.planning.domain.PlanningGrid;
import com.ondemandmonitoring.planning.dto.PlannedRoute;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
@ActiveProfiles("e2e")
class DirectPlanningE2ETest {

    private static final double TOLERANCE = 1.0e-9;
    private static final double FEASIBLE_X = 200.0;
    private static final double FEASIBLE_Y = -280.0;
    private static final double INFEASIBLE_X = -200.0;
    private static final double INFEASIBLE_Y = -280.0;
    private static final GeometryFactory GEOMETRY_FACTORY =
            new GeometryFactory(new PrecisionModel(), 4326);

    @Autowired
    private MissionPlanningService missionPlanningService;

    @Autowired
    private MissionRepository missionRepository;

    @Autowired
    private MissionPlanRepository missionPlanRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private PlanningEnvironment planningEnvironment;

    @Autowired
    private SimulationHomeProvider simulationHomeProvider;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Value("${planning.energy.battery-capacity-mah:5000.0}")
    private double batteryCapacityMah;

    @Value("${planning.energy.ascend-current-a:7.5}")
    private double ascendCurrentA;

    @Value("${planning.energy.cruise-current-a:5.5}")
    private double cruiseCurrentA;

    @Value("${planning.energy.cruise-speed-mps:2.0}")
    private double cruiseSpeedMps;

    @Value("${planning.energy.ascend-speed-mps:1.0}")
    private double ascendSpeedMps;

    @Value("${planning.energy.safety-reserve-percent:20.0}")
    private double safetyReservePercent;

    @Test
    void directPlanningPersistsFeasiblePlanThenReplansInfeasibleWithoutStaleRows() {
        long plansBefore = count("mission_plans");
        long waypointsBefore = count("plan_waypoints");
        Order sourceOrder = orderRepository.findAll().stream().findFirst().orElseThrow();
        Order testOrder = orderRepository.saveAndFlush(copyOrder(sourceOrder, point(FEASIBLE_X, FEASIBLE_Y)));

        Mission mission = new Mission();
        mission.setMissionCode("E2E_DIRECT_PLANNING_TEST_" + UUID.randomUUID().toString().substring(0, 8));
        mission.setStatus(MissionStatus.CREATED);
        mission.setOrder(testOrder);
        mission = missionRepository.saveAndFlush(mission);

        SimulationPoint home = simulationHomeProvider.home();
        EnvironmentSample homeSample = planningEnvironment.sample(home.x(), home.y());
        MissionPlan feasibleResult = missionPlanningService.generateDirectPlan(mission.getId());
        entityManager.flush();
        entityManager.clear();

        MissionPlan persistedFeasible = missionPlanRepository.findByMissionId(mission.getId()).orElseThrow();
        List<PlanWaypoint> feasibleWaypoints = persistedFeasible.getWaypoints().stream()
                .sorted(Comparator.comparingInt(PlanWaypoint::getSequence))
                .toList();

        double expectedDistance = Math.hypot(FEASIBLE_X - home.x(), FEASIBLE_Y - home.y());
        double expectedClimb = Math.max(0.0,
                persistedFeasible.getMaxPlannedAltitudeM() - homeSample.surfaceElevationM());
        double expectedAscendDuration = expectedClimb / ascendSpeedMps;
        double expectedCruiseDuration = expectedDistance / cruiseSpeedMps;
        double expectedDuration = expectedAscendDuration + expectedCruiseDuration;
        double expectedAscendMah = ascendCurrentA * 1000.0 * expectedAscendDuration / 3600.0;
        double expectedCruiseMah = cruiseCurrentA * 1000.0 * expectedCruiseDuration / 3600.0;
        double expectedEnergyMah = expectedAscendMah + expectedCruiseMah;
        double expectedBatteryUsed = expectedEnergyMah / batteryCapacityMah * 100.0;
        double expectedRequiredBattery = expectedBatteryUsed + safetyReservePercent;

        assertThat(feasibleResult.getId()).isEqualTo(persistedFeasible.getId());
        assertThat(persistedFeasible.getPlanningAlgorithm()).isEqualTo(PlanningAlgorithm.DIRECT);
        assertThat(persistedFeasible.getFeasibilityStatus()).isEqualTo(FeasibilityStatus.FEASIBLE);
        assertThat(persistedFeasible.getPlannedDistanceM()).isCloseTo(expectedDistance, withinTolerance());
        assertThat(persistedFeasible.getPlannedDurationSec()).isCloseTo(expectedDuration, withinTolerance());
        assertThat(persistedFeasible.getEstimatedEnergyMah()).isCloseTo(expectedEnergyMah, withinTolerance());
        assertThat(persistedFeasible.getEstimatedBatteryUsedPercent()).isCloseTo(expectedBatteryUsed, withinTolerance());
        assertThat(persistedFeasible.getRequiredBatteryPercent()).isCloseTo(expectedRequiredBattery, withinTolerance());
        assertThat(feasibleWaypoints).hasSize(2);
        assertWaypoint(feasibleWaypoints.get(0), 0, WaypointReason.START, home.x(), home.y(),
                persistedFeasible.getMaxPlannedAltitudeM());
        assertWaypoint(feasibleWaypoints.get(1), 1, WaypointReason.TARGET, FEASIBLE_X, FEASIBLE_Y,
                persistedFeasible.getMaxPlannedAltitudeM());
        assertThat(countForMission("mission_plans", mission.getId())).isEqualTo(1);
        assertThat(countWaypointsForMission(mission.getId())).isEqualTo(2);

        printEvidence(
                mission,
                testOrder,
                home,
                homeSample,
                persistedFeasible,
                feasibleWaypoints,
                expectedDistance,
                expectedClimb,
                expectedAscendDuration,
                expectedCruiseDuration,
                expectedDuration,
                expectedAscendMah,
                expectedCruiseMah,
                expectedEnergyMah,
                expectedBatteryUsed,
                expectedRequiredBattery);

        String planId = persistedFeasible.getId();
        testOrder = orderRepository.findById(testOrder.getId()).orElseThrow();
        testOrder.setPoint(point(INFEASIBLE_X, INFEASIBLE_Y));
        orderRepository.saveAndFlush(testOrder);

        PlannedRoute rejectedRoute = missionPlanningService.validateDirectRoute(mission.getId());
        assertThat(rejectedRoute.feasible()).isFalse();
        assertThat(rejectedRoute.failureReason()).contains("AIRPORT");
        MissionPlan infeasibleResult = missionPlanningService.generateDirectPlan(mission.getId());
        entityManager.flush();
        entityManager.clear();

        MissionPlan persistedInfeasible = missionPlanRepository.findByMissionId(mission.getId()).orElseThrow();
        assertThat(infeasibleResult.getId()).isEqualTo(planId);
        assertThat(persistedInfeasible.getId()).isEqualTo(planId);
        assertThat(persistedInfeasible.getPlanningAlgorithm()).isEqualTo(PlanningAlgorithm.DIRECT);
        assertThat(persistedInfeasible.getFeasibilityStatus()).isEqualTo(FeasibilityStatus.NO_SAFE_ROUTE);
        assertThat(persistedInfeasible.getWaypoints()).isEmpty();
        assertThat(persistedInfeasible.getPlannedDurationSec()).isNull();
        assertThat(persistedInfeasible.getPlannedCruiseSpeedMps()).isNull();
        assertThat(persistedInfeasible.getEstimatedEnergyMah()).isNull();
        assertThat(persistedInfeasible.getEstimatedBatteryUsedPercent()).isNull();
        assertThat(persistedInfeasible.getAvailableBatteryPercentAtPlanning()).isNull();
        assertThat(persistedInfeasible.getSafetyReservePercent()).isNull();
        assertThat(persistedInfeasible.getRequiredBatteryPercent()).isNull();
        assertThat(countForMission("mission_plans", mission.getId())).isEqualTo(1);
        assertThat(countWaypointsForMission(mission.getId())).isZero();
        System.out.printf("E2E|REPLAN|target=%.3f,%.3f|reason=%s|status=%s|planId=%s|waypoints=%d|energy=%s|plansBefore=%d|waypointsBefore=%d%n",
                INFEASIBLE_X, INFEASIBLE_Y, rejectedRoute.failureReason().replace('|', '/'),
                persistedInfeasible.getFeasibilityStatus(), persistedInfeasible.getId(),
                persistedInfeasible.getWaypoints().size(), String.valueOf(persistedInfeasible.getEstimatedEnergyMah()),
                plansBefore, waypointsBefore);
    }

    private Order copyOrder(Order source, Point target) {
        Order order = new Order();
        order.setCustomer(source.getCustomer());
        order.setTitle("E2E_DIRECT_PLANNING_TEST_ORDER");
        order.setPurpose(source.getPurpose());
        order.setService(source.getService());
        order.setDescription("Temporary transactional DIRECT planning E2E record");
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

    private void assertWaypoint(
            PlanWaypoint waypoint,
            int sequence,
            WaypointReason reason,
            double x,
            double y,
            double worldZ) {
        assertThat(waypoint.getSequence()).isEqualTo(sequence);
        assertThat(waypoint.getReason()).isEqualTo(reason);
        assertThat(waypoint.getSimX()).isCloseTo(x, withinTolerance());
        assertThat(waypoint.getSimY()).isCloseTo(y, withinTolerance());
        assertThat(waypoint.getAltitudeM()).isCloseTo(worldZ, withinTolerance());
        assertThat(waypoint.getPlannedSpeedMps()).isNull();
    }

    private org.assertj.core.data.Offset<Double> withinTolerance() {
        return org.assertj.core.data.Offset.offset(TOLERANCE);
    }

    private long count(String table) {
        return jdbcTemplate.queryForObject("select count(*) from " + table, Long.class);
    }

    private long countForMission(String table, String missionId) {
        return jdbcTemplate.queryForObject(
                "select count(*) from " + table + " where mission_id = ?", Long.class, missionId);
    }

    private long countWaypointsForMission(String missionId) {
        return jdbcTemplate.queryForObject("""
                select count(*)
                from plan_waypoints pw
                join mission_plans mp on mp.id = pw.mission_plan_id
                where mp.mission_id = ?
                """, Long.class, missionId);
    }

    private void printEvidence(
            Mission mission,
            Order order,
            SimulationPoint home,
            EnvironmentSample homeSample,
            MissionPlan feasible,
            List<PlanWaypoint> waypoints,
            double expectedDistance,
            double climb,
            double ascendDuration,
            double cruiseDuration,
            double expectedDuration,
            double ascendMah,
            double cruiseMah,
            double expectedEnergy,
            double expectedBatteryUsed,
            double expectedRequiredBattery) {
        PlanningGrid grid = planningEnvironment.grid();
        System.out.printf("E2E|GRID|world=%s|coordinateSystem=%s|bounds=%.3f,%.3f,%.3f,%.3f|resolution=%.3f|size=%dx%d%n",
                grid.world(), grid.coordinateSystem(), grid.bounds().minX(), grid.bounds().maxX(),
                grid.bounds().minY(), grid.bounds().maxY(), grid.resolutionM(), grid.width(), grid.height());
        printSample("HOME", homeSample);
        printSample("FEASIBLE_TARGET", planningEnvironment.sample(FEASIBLE_X, FEASIBLE_Y));
        printSample("RESTRICTED_AIRPORT", planningEnvironment.sample(INFEASIBLE_X, INFEASIBLE_Y));
        printSample("NO_DATA", planningEnvironment.sample(450.0, 686.497));
        printSample("OUTSIDE", planningEnvironment.sample(451.0, 0.0));
        System.out.printf("E2E|MISSION|id=%s|code=%s|orderId=%s|home=%.3f,%.3f|target=%.3f,%.3f%n",
                mission.getId(), mission.getMissionCode(), order.getId(), home.x(), home.y(), FEASIBLE_X, FEASIBLE_Y);
        System.out.printf("E2E|FEASIBLE|planId=%s|algorithm=%s|status=%s|distance=%.12f|maxWorldZ=%.12f|planningMs=%d%n",
                feasible.getId(), feasible.getPlanningAlgorithm(), feasible.getFeasibilityStatus(),
                feasible.getPlannedDistanceM(), feasible.getMaxPlannedAltitudeM(), feasible.getPlanningTimeMs());
        System.out.printf("E2E|ENERGY|homeWorldZ=%.12f|climb=%.12f|cruiseSpeed=%.12f|ascendSpeed=%.12f|ascendSec=%.12f|cruiseSec=%.12f|duration=%.12f|ascendMah=%.12f|cruiseMah=%.12f|energyMah=%.12f|capacityMah=%.12f|batteryUsed=%.12f|reserve=%.12f|required=%.12f|available=%s%n",
                homeSample.surfaceElevationM(), climb, cruiseSpeedMps, ascendSpeedMps, ascendDuration,
                cruiseDuration, feasible.getPlannedDurationSec(), ascendMah, cruiseMah,
                feasible.getEstimatedEnergyMah(), batteryCapacityMah, feasible.getEstimatedBatteryUsedPercent(),
                feasible.getSafetyReservePercent(), feasible.getRequiredBatteryPercent(),
                String.valueOf(feasible.getAvailableBatteryPercentAtPlanning()));
        System.out.printf("E2E|DIFF|distance=%.12g|duration=%.12g|energyMah=%.12g|batteryUsed=%.12g|required=%.12g%n",
                feasible.getPlannedDistanceM() - expectedDistance,
                feasible.getPlannedDurationSec() - expectedDuration,
                feasible.getEstimatedEnergyMah() - expectedEnergy,
                feasible.getEstimatedBatteryUsedPercent() - expectedBatteryUsed,
                feasible.getRequiredBatteryPercent() - expectedRequiredBattery);
        for (PlanWaypoint waypoint : waypoints) {
            System.out.printf("E2E|WAYPOINT|sequence=%d|reason=%s|x=%.12f|y=%.12f|worldZ=%.12f|speed=%s%n",
                    waypoint.getSequence(), waypoint.getReason(), waypoint.getSimX(), waypoint.getSimY(),
                    waypoint.getAltitudeM(), String.valueOf(waypoint.getPlannedSpeedMps()));
        }
    }

    private void printSample(String label, EnvironmentSample sample) {
        System.out.printf("E2E|SAMPLE|label=%s|x=%.3f|y=%.3f|inside=%s|terrain=%s|obstacle=%s|surface=%s|restricted=%s|zone=%s%n",
                label, sample.simX(), sample.simY(), sample.insideWorldBounds(),
                String.valueOf(sample.terrainElevationM()), String.valueOf(sample.obstacleHeightM()),
                String.valueOf(sample.surfaceElevationM()), sample.restricted(),
                String.valueOf(sample.restrictedZoneCode()));
    }
}
