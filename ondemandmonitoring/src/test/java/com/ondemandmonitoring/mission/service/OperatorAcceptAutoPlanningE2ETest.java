package com.ondemandmonitoring.mission.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.ondemandmonitoring.device.domain.Device;
import com.ondemandmonitoring.device.enums.DeviceStatus;
import com.ondemandmonitoring.device.repository.DeviceRepository;
import com.ondemandmonitoring.mission.dto.request.AssignDeviceRequest;
import com.ondemandmonitoring.mission.dto.request.AssignStaffRequest;
import com.ondemandmonitoring.mission.dto.request.MissionCreateRequest;
import com.ondemandmonitoring.mission.dto.response.MissionResponse;
import com.ondemandmonitoring.mission.enums.MissionStatus;
import com.ondemandmonitoring.mission.repository.MissionPlanRepository;
import com.ondemandmonitoring.order.domain.Order;
import com.ondemandmonitoring.order.repository.OrderRepository;
import com.ondemandmonitoring.user.domain.User;
import com.ondemandmonitoring.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
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
    @Autowired private DeviceRepository deviceRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private MissionPlanRepository missionPlanRepository;
    @Autowired private EntityManager entityManager;
    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    void operatorAcceptDefersPlanningUntilFreshTelemetryPreflight() {
        Order source = orderRepository.findAll().stream().findFirst().orElseThrow();
        Order order = orderRepository.saveAndFlush(copyOrder(source, point(200.0, -280.0)));
        Device device = deviceRepository.findAll().stream().findFirst().orElseThrow();
        device.setStatus(DeviceStatus.AVAILABLE);
        deviceRepository.saveAndFlush(device);

        User staff = userRepository.findAll().stream()
                .filter(u -> Boolean.TRUE.equals(u.getIsActive()))
                .findFirst().orElseGet(() -> {
                    User u = new User();
                    u.setId("OP-E2E");
                    u.setEmail("operator@example.com");
                    u.setIsActive(true);
                    return userRepository.saveAndFlush(u);
                });
        String staffId = staff.getId();

        java.time.LocalDate targetDate = order.getPreferredDateFrom() != null ? order.getPreferredDateFrom() : java.time.LocalDate.now(java.time.ZoneOffset.UTC);
        java.time.Instant startAt = targetDate.atTime(9, 0).toInstant(java.time.ZoneOffset.UTC);
        java.time.Instant endAt = targetDate.atTime(11, 0).toInstant(java.time.ZoneOffset.UTC);

        MissionCreateRequest createRequest = new MissionCreateRequest();
        createRequest.setOrderId(order.getId());
        createRequest.setScheduledStartAt(startAt);
        createRequest.setScheduledEndAt(endAt);
        MissionResponse created = missionService.createMission(createRequest);

        AssignDeviceRequest deviceRequest = new AssignDeviceRequest();
        deviceRequest.setDeviceId(device.getId());
        MissionResponse deviceAssigned = missionService.assignDevice(created.getId(), deviceRequest);

        AssignStaffRequest staffRequest = new AssignStaffRequest();
        staffRequest.setStaffId(staffId);
        MissionResponse staffAssigned = missionService.assignStaff(deviceAssigned.getId(), staffRequest);

        assertThat(staffAssigned.getStatus()).isEqualTo(MissionStatus.WAITING_OPERATOR_ACCEPTANCE);

        MissionResponse accepted = missionService.acceptMission(staffAssigned.getId(), staffId);
        entityManager.flush();
        entityManager.clear();

        assertThat(accepted.getStatus()).isEqualTo(MissionStatus.SCHEDULED);
        assertThat(accepted.getPlan()).isNotNull();
        assertThat(missionPlanRepository.findByMissionId(accepted.getId())).isPresent();
        assertThat(countPlans(accepted.getId())).isEqualTo(1L);
        assertThat(countWaypoints(accepted.getId())).isGreaterThan(0L);
    }

    private Order copyOrder(Order source, Point target) {
        Order order = new Order();
        order.setCustomer(source.getCustomer());
        order.setTitle("E2E_OPERATOR_ACCEPT_AUTO_PLAN");
        order.setService(source.getService());
        order.setDescription("Temporary transactional operator accept auto-planning E2E record");
        order.setAddress("LOCAL_SIMULATION_METERS_GAZEBO_XY");
        order.setPoint(target);
        order.setPreferredDateFrom(source.getPreferredDateFrom());
        order.setPreferredDateTo(source.getPreferredDateTo());
        order.setPreferredTime(source.getPreferredTime());
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
