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
class AStarShortestPlanningE2ETest {

    private static final double DETOUR_X = -400.0;
    private static final double DETOUR_Y = -280.0;
    private static final double RESTRICTED_X = -200.0;
    private static final double RESTRICTED_Y = -280.0;
    private static final double TOLERANCE = 1.0e-9;
    private static final GeometryFactory GEOMETRY_FACTORY =
            new GeometryFactory(new PrecisionModel(), 4326);

    @Autowired private MissionPlanningService missionPlanningService;
    @Autowired private MissionRepository missionRepository;
    @Autowired private MissionPlanRepository missionPlanRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private SimulationHomeProvider simulationHomeProvider;
    @Autowired private PlanningEnvironment planningEnvironment;
    @Autowired private EntityManager entityManager;
    @Autowired private JdbcTemplate jdbcTemplate;

    @Value("${planning.energy.battery-capacity-mah:5000.0}") private double batteryCapacityMah;
    @Value("${planning.energy.ascend-current-a:7.5}") private double ascendCurrentA;
    @Value("${planning.energy.cruise-current-a:5.5}") private double cruiseCurrentA;
    @Value("${planning.energy.cruise-speed-mps:2.0}") private double cruiseSpeedMps;
    @Value("${planning.energy.ascend-speed-mps:1.0}") private double ascendSpeedMps;
    @Value("${planning.energy.safety-reserve-percent:20.0}") private double reservePercent;

    @Test
    void simpleRouteComparesDirectAndAStarOnSamePersistedMissionPlan() {
        Order source = orderRepository.findAll().stream().findFirst().orElseThrow();
        Order order = orderRepository.saveAndFlush(copyOrder(source, point(200.0, -280.0)));
        Mission mission = new Mission();
        mission.setMissionCode("E2E_ASTAR_SHORTEST_SIMPLE_" + UUID.randomUUID().toString().substring(0, 8));
        mission.setStatus(MissionStatus.CREATED);
        mission.setOrder(order);
        mission = missionRepository.saveAndFlush(mission);

        MissionPlan direct = missionPlanningService.generateDirectPlan(mission.getId());
        entityManager.flush();
        String planId = direct.getId();
        double directDistance = direct.getPlannedDistanceM();
        double directWorldZ = direct.getMaxPlannedAltitudeM();
        double directDuration = direct.getPlannedDurationSec();
        double directEnergy = direct.getEstimatedEnergyMah();
        double directBattery = direct.getEstimatedBatteryUsedPercent();
        double directRequired = direct.getRequiredBatteryPercent();
        long directPlanningMs = direct.getPlanningTimeMs();

        MissionPlan aStar = missionPlanningService.generateAStarShortestPlan(mission.getId());
        entityManager.flush();
        entityManager.clear();
        MissionPlan persisted = missionPlanRepository.findByMissionId(mission.getId()).orElseThrow();

        assertThat(aStar.getId()).isEqualTo(planId);
        assertThat(persisted.getId()).isEqualTo(planId);
        assertThat(persisted.getPlanningAlgorithm()).isEqualTo(PlanningAlgorithm.ASTAR_SHORTEST);
        assertThat(persisted.getFeasibilityStatus()).isEqualTo(FeasibilityStatus.FEASIBLE);
        assertThat(persisted.getPlannedDistanceM()).isCloseTo(200.0, offset());
        assertThat(persisted.getWaypoints()).hasSize(2);
        assertThat(countForMission("mission_plans", mission.getId())).isEqualTo(1);

        System.out.printf("ASTAR_E2E|SIMPLE|plan=%s|directDistance=%.12f|astarDistance=%.12f|directWorldZ=%.12f|astarWorldZ=%.12f|directDuration=%.12f|astarDuration=%.12f|directEnergy=%.12f|astarEnergy=%.12f|directBattery=%.12f|astarBattery=%.12f|directRequired=%.12f|astarRequired=%.12f|directPlanningMs=%d|astarPlanningMs=%d|waypoints=%d%n",
                planId, directDistance, persisted.getPlannedDistanceM(), directWorldZ,
                persisted.getMaxPlannedAltitudeM(), directDuration, persisted.getPlannedDurationSec(),
                directEnergy, persisted.getEstimatedEnergyMah(), directBattery,
                persisted.getEstimatedBatteryUsedPercent(), directRequired,
                persisted.getRequiredBatteryPercent(), directPlanningMs,
                persisted.getPlanningTimeMs(), persisted.getWaypoints().size());
    }

    @Test
    void directToAStarDetourPersistsThenInfeasibleReplanClearsState() {
        long plansBefore = count("mission_plans");
        long waypointsBefore = count("plan_waypoints");
        Order source = orderRepository.findAll().stream().findFirst().orElseThrow();
        Order order = orderRepository.saveAndFlush(copyOrder(source, point(DETOUR_X, DETOUR_Y)));
        Mission mission = new Mission();
        mission.setMissionCode("E2E_ASTAR_SHORTEST_" + UUID.randomUUID().toString().substring(0, 8));
        mission.setStatus(MissionStatus.CREATED);
        mission.setOrder(order);
        mission = missionRepository.saveAndFlush(mission);

        MissionPlan direct = missionPlanningService.generateDirectPlan(mission.getId());
        entityManager.flush();
        assertThat(direct.getPlanningAlgorithm()).isEqualTo(PlanningAlgorithm.DIRECT);
        assertThat(direct.getFeasibilityStatus()).isEqualTo(FeasibilityStatus.NO_SAFE_ROUTE);
        String planId = direct.getId();

        MissionPlan aStar = missionPlanningService.generateAStarShortestPlan(mission.getId());
        entityManager.flush();
        entityManager.clear();
        MissionPlan persisted = missionPlanRepository.findByMissionId(mission.getId()).orElseThrow();
        List<PlanWaypoint> waypoints = persisted.getWaypoints().stream()
                .sorted(Comparator.comparingInt(PlanWaypoint::getSequence))
                .toList();

        assertThat(aStar.getId()).isEqualTo(planId);
        assertThat(persisted.getId()).isEqualTo(planId);
        assertThat(persisted.getPlanningAlgorithm()).isEqualTo(PlanningAlgorithm.ASTAR_SHORTEST);
        assertThat(persisted.getFeasibilityStatus()).isEqualTo(FeasibilityStatus.FEASIBLE);
        assertThat(persisted.getPlannedDistanceM()).isGreaterThan(400.0);
        assertThat(waypoints).hasSizeGreaterThanOrEqualTo(2);
        assertThat(waypoints.getFirst().getReason()).isEqualTo(WaypointReason.START);
        assertThat(waypoints.getLast().getReason()).isEqualTo(WaypointReason.TARGET);
        assertThat(waypoints.subList(1, waypoints.size() - 1))
                .allMatch(waypoint -> waypoint.getReason() == WaypointReason.CRUISE);
        assertThat(waypoints).allMatch(waypoint ->
                Math.abs(waypoint.getAltitudeM() - persisted.getMaxPlannedAltitudeM()) <= TOLERANCE);
        assertThat(countForMission("mission_plans", mission.getId())).isEqualTo(1);
        assertThat(countWaypoints(mission.getId())).isEqualTo(waypoints.size());

        SimulationPoint home = simulationHomeProvider.home();
        EnvironmentSample homeSample = planningEnvironment.sample(home.x(), home.y());
        double distance = persisted.getPlannedDistanceM();
        double climb = Math.max(0.0, persisted.getMaxPlannedAltitudeM() - homeSample.surfaceElevationM());
        double ascendSec = climb / ascendSpeedMps;
        double cruiseSec = distance / cruiseSpeedMps;
        double expectedDuration = ascendSec + cruiseSec;
        double expectedEnergy = ascendCurrentA * 1000.0 * ascendSec / 3600.0
                + cruiseCurrentA * 1000.0 * cruiseSec / 3600.0;
        double expectedBatteryUsed = expectedEnergy / batteryCapacityMah * 100.0;
        double expectedRequired = expectedBatteryUsed + reservePercent;
        assertThat(persisted.getPlannedDurationSec()).isCloseTo(expectedDuration, offset());
        assertThat(persisted.getEstimatedEnergyMah()).isCloseTo(expectedEnergy, offset());
        assertThat(persisted.getEstimatedBatteryUsedPercent()).isCloseTo(expectedBatteryUsed, offset());
        assertThat(persisted.getRequiredBatteryPercent()).isCloseTo(expectedRequired, offset());
        assertThat(persisted.getAvailableBatteryPercentAtPlanning()).isNull();

        System.out.printf("ASTAR_E2E|FEASIBLE|mission=%s|order=%s|plan=%s|algorithm=%s|distance=%.12f|worldZ=%.12f|planningMs=%d|duration=%.12f|energyMah=%.12f|batteryUsed=%.12f|required=%.12f|waypoints=%d%n",
                mission.getId(), order.getId(), planId, persisted.getPlanningAlgorithm(), distance,
                persisted.getMaxPlannedAltitudeM(), persisted.getPlanningTimeMs(),
                persisted.getPlannedDurationSec(), persisted.getEstimatedEnergyMah(),
                persisted.getEstimatedBatteryUsedPercent(), persisted.getRequiredBatteryPercent(), waypoints.size());
        System.out.printf("ASTAR_E2E|ENERGY_DIFF|duration=%.12g|energy=%.12g|batteryUsed=%.12g|required=%.12g|euclidean=400.000000000000%n",
                persisted.getPlannedDurationSec() - expectedDuration,
                persisted.getEstimatedEnergyMah() - expectedEnergy,
                persisted.getEstimatedBatteryUsedPercent() - expectedBatteryUsed,
                persisted.getRequiredBatteryPercent() - expectedRequired);
        for (PlanWaypoint waypoint : waypoints) {
            System.out.printf("ASTAR_E2E|WP|sequence=%d|reason=%s|x=%.3f|y=%.3f|z=%.3f|speed=%s%n",
                    waypoint.getSequence(), waypoint.getReason(), waypoint.getSimX(), waypoint.getSimY(),
                    waypoint.getAltitudeM(), String.valueOf(waypoint.getPlannedSpeedMps()));
        }

        order = orderRepository.findById(order.getId()).orElseThrow();
        order.setPoint(point(RESTRICTED_X, RESTRICTED_Y));
        orderRepository.saveAndFlush(order);
        MissionPlan infeasible = missionPlanningService.generateAStarShortestPlan(mission.getId());
        entityManager.flush();
        entityManager.clear();
        MissionPlan persistedInfeasible = missionPlanRepository.findByMissionId(mission.getId()).orElseThrow();
        assertThat(infeasible.getId()).isEqualTo(planId);
        assertThat(persistedInfeasible.getId()).isEqualTo(planId);
        assertThat(persistedInfeasible.getFeasibilityStatus()).isEqualTo(FeasibilityStatus.NO_SAFE_ROUTE);
        assertThat(persistedInfeasible.getWaypoints()).isEmpty();
        assertThat(persistedInfeasible.getPlannedDurationSec()).isNull();
        assertThat(persistedInfeasible.getEstimatedEnergyMah()).isNull();
        assertThat(persistedInfeasible.getRequiredBatteryPercent()).isNull();
        assertThat(countForMission("mission_plans", mission.getId())).isEqualTo(1);
        assertThat(countWaypoints(mission.getId())).isZero();
        System.out.printf("ASTAR_E2E|REPLAN|plan=%s|status=%s|waypoints=%d|energy=%s|plansBefore=%d|waypointsBefore=%d%n",
                planId, persistedInfeasible.getFeasibilityStatus(), persistedInfeasible.getWaypoints().size(),
                String.valueOf(persistedInfeasible.getEstimatedEnergyMah()), plansBefore, waypointsBefore);
    }

    private Order copyOrder(Order source, Point target) {
        Order order = new Order();
        order.setCustomer(source.getCustomer());
        order.setTitle("E2E_ASTAR_SHORTEST_ORDER");
        order.setPurpose(source.getPurpose());
        order.setService(source.getService());
        order.setDescription("Temporary transactional ASTAR_SHORTEST E2E record");
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

    private long count(String table) {
        return jdbcTemplate.queryForObject("select count(*) from " + table, Long.class);
    }

    private long countForMission(String table, String missionId) {
        return jdbcTemplate.queryForObject(
                "select count(*) from " + table + " where mission_id = ?", Long.class, missionId);
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
}
