package com.ondemandmonitoring.mission.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ondemandmonitoring.drone.domain.Drone;
import com.ondemandmonitoring.drone.enums.DroneStatus;
import com.ondemandmonitoring.drone.repository.DroneRepository;
import com.ondemandmonitoring.mission.domain.MissionPlan;
import com.ondemandmonitoring.mission.domain.PlanWaypoint;
import com.ondemandmonitoring.mission.dto.response.MissionResponse;
import com.ondemandmonitoring.mission.enums.FeasibilityStatus;
import com.ondemandmonitoring.mission.enums.MissionStatus;
import com.ondemandmonitoring.mission.enums.PlanningAlgorithm;
import com.ondemandmonitoring.mission.repository.MissionPlanRepository;
import com.ondemandmonitoring.order.domain.Order;
import com.ondemandmonitoring.order.repository.OrderRepository;
import jakarta.persistence.EntityManager;
import java.util.Comparator;
import java.util.List;
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
class OperatorAcceptAutoPlanningE2ETest {

    private static final GeometryFactory GEOMETRY_FACTORY =
            new GeometryFactory(new PrecisionModel(), 4326);

    @Autowired private IMissionService missionService;
    @Autowired private OrderRepository orderRepository;
    @Autowired private DroneRepository droneRepository;
    @Autowired private MissionPlanRepository missionPlanRepository;
    @Autowired private EntityManager entityManager;
    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    void operatorAcceptGeneratesEnergyAwareMissionPlanAndKeepsPreflightBehindFeasiblePlan() {
        Order source = orderRepository.findAll().stream().findFirst().orElseThrow();
        Order order = orderRepository.saveAndFlush(copyOrder(source, point(200.0, -280.0)));
        Drone drone = droneRepository.findAll().stream().findFirst().orElseThrow();
        drone.setStatus(DroneStatus.AVAILABLE);
        droneRepository.saveAndFlush(drone);

        MissionResponse created = missionService.createMissionForOrder(order.getId());
        MissionResponse droneAssigned = missionService.assignDrone(created.getId(), drone.getId());
        MissionResponse operatorAssigned = missionService.assignOperator(droneAssigned.getId(), "OP-E2E");

        assertThat(operatorAssigned.getStatus()).isEqualTo(MissionStatus.WAITING_OPERATOR_ACCEPTANCE);

        MissionResponse accepted = missionService.acceptMission(operatorAssigned.getId(), "OP-E2E");
        entityManager.flush();
        entityManager.clear();

        MissionPlan persisted = missionPlanRepository.findByMissionId(accepted.getId()).orElseThrow();
        List<PlanWaypoint> waypoints = persisted.getWaypoints().stream()
                .sorted(Comparator.comparingInt(PlanWaypoint::getSequence))
                .toList();

        assertThat(accepted.getStatus()).isEqualTo(MissionStatus.SCHEDULED);
        assertThat(accepted.getPlan()).isNotNull();
        assertThat(accepted.getPlan().getPlanningAlgorithm()).isEqualTo(PlanningAlgorithm.ASTAR_ENERGY_AWARE);
        assertThat(persisted.getPlanningAlgorithm()).isEqualTo(PlanningAlgorithm.ASTAR_ENERGY_AWARE);
        assertThat(persisted.getFeasibilityStatus()).isEqualTo(FeasibilityStatus.FEASIBLE);
        assertThat(persisted.getPlannedDistanceM()).isPositive();
        assertThat(persisted.getPlannedDurationSec()).isPositive();
        assertThat(persisted.getMaxPlannedAltitudeM()).isPositive();
        assertThat(persisted.getEstimatedEnergyMah()).isPositive();
        assertThat(persisted.getEstimatedBatteryUsedPercent()).isPositive();
        assertThat(persisted.getRequiredBatteryPercent()).isGreaterThan(persisted.getEstimatedBatteryUsedPercent());
        assertThat(waypoints).isNotEmpty();
        assertThat(countPlans(accepted.getId())).isEqualTo(1);
        assertThat(countWaypoints(accepted.getId())).isEqualTo(waypoints.size());

        assertThat(missionService.getMissionPlan(accepted.getId()).getWaypoints()).hasSameSizeAs(waypoints);

        assertThatThrownBy(() -> missionService.acceptMission(accepted.getId(), "OP-E2E"))
                .hasMessageContaining("must be WAITING_OPERATOR_ACCEPTANCE");
        assertThat(countPlans(accepted.getId())).isEqualTo(1);
        assertThat(countWaypoints(accepted.getId())).isEqualTo(waypoints.size());

        System.out.printf("OPERATOR_AUTO_PLAN_E2E|mission=%s|plan=%s|algorithm=%s|feasibility=%s|distance=%.12f|duration=%.12f|worldZ=%.12f|energy=%.12f|battery=%.12f|required=%.12f|available=%s|planningMs=%d|waypoints=%d|plans=%d|status=%s%n",
                accepted.getId(), persisted.getId(), persisted.getPlanningAlgorithm(), persisted.getFeasibilityStatus(),
                persisted.getPlannedDistanceM(), persisted.getPlannedDurationSec(), persisted.getMaxPlannedAltitudeM(),
                persisted.getEstimatedEnergyMah(), persisted.getEstimatedBatteryUsedPercent(),
                persisted.getRequiredBatteryPercent(), String.valueOf(persisted.getAvailableBatteryPercentAtPlanning()),
                persisted.getPlanningTimeMs(), waypoints.size(), countPlans(accepted.getId()), accepted.getStatus());
    }

    private Order copyOrder(Order source, Point target) {
        Order order = new Order();
        order.setCustomer(source.getCustomer());
        order.setTitle("E2E_OPERATOR_ACCEPT_AUTO_PLAN");
        order.setPurpose(source.getPurpose());
        order.setService(source.getService());
        order.setDescription("Temporary transactional operator accept auto-planning E2E record");
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
}
