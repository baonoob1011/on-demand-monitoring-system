package com.ondemandmonitoring.mission.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.ondemandmonitoring.device.domain.Device;
import com.ondemandmonitoring.device.enums.DeviceStatus;
import com.ondemandmonitoring.device.repository.DeviceRepository;
import com.ondemandmonitoring.finance.domain.Invoice;
import com.ondemandmonitoring.finance.domain.Quote;
import com.ondemandmonitoring.finance.enums.InvoiceStatus;
import com.ondemandmonitoring.finance.enums.QuoteStatus;
import com.ondemandmonitoring.finance.repository.InvoiceRepository;
import com.ondemandmonitoring.finance.repository.QuoteRepository;
import com.ondemandmonitoring.mission.dto.request.AssignDeviceRequest;
import com.ondemandmonitoring.mission.dto.request.AssignStaffRequest;
import com.ondemandmonitoring.mission.dto.request.ChecklistAssignmentSelectionRequest;
import com.ondemandmonitoring.mission.dto.request.ExecutionPlanFinalizeRequest;
import com.ondemandmonitoring.mission.dto.request.MissionCreateRequest;
import com.ondemandmonitoring.mission.dto.response.MissionResponse;
import com.ondemandmonitoring.mission.enums.MissionStatus;
import com.ondemandmonitoring.mission.repository.MissionPlanRepository;
import com.ondemandmonitoring.order.domain.Order;
import com.ondemandmonitoring.order.domain.OrderChecklistItem;
import com.ondemandmonitoring.order.enums.ChecklistReviewStatus;
import com.ondemandmonitoring.order.enums.OrderChecklistSourceType;
import com.ondemandmonitoring.order.enums.RequiredEvidenceType;
import com.ondemandmonitoring.order.repository.OrderChecklistItemRepository;
import com.ondemandmonitoring.order.repository.OrderRepository;
import com.ondemandmonitoring.role.enumeration.RoleCode;
import com.ondemandmonitoring.role.repository.RoleRepository;
import com.ondemandmonitoring.user.domain.User;
import com.ondemandmonitoring.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;

@SpringBootTest
@Transactional
@ActiveProfiles("e2e")
@Import(OperatorAcceptAutoPlanningE2ETest.TestSchemaBootstrap.class)
class OperatorAcceptAutoPlanningE2ETest {

    private static final GeometryFactory GEOMETRY_FACTORY =
            new GeometryFactory(new PrecisionModel(), 4326);

    @Autowired private IMissionService missionService;
    @Autowired private OrderRepository orderRepository;
    @Autowired private DeviceRepository deviceRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private QuoteRepository quoteRepository;
    @Autowired private InvoiceRepository invoiceRepository;
    @Autowired private OrderChecklistItemRepository orderChecklistItemRepository;
    @Autowired private IMissionChecklistAssignmentService checklistAssignmentService;
    @Autowired private IExecutionPlanService executionPlanService;
    @Autowired private MissionPlanRepository missionPlanRepository;
    @Autowired private EntityManager entityManager;
    @Autowired private JdbcTemplate jdbcTemplate;

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }


    @TestConfiguration(proxyBeanMethods = false)
    static class TestSchemaBootstrap {
        @Bean
        @org.springframework.core.annotation.Order(0)
        ApplicationRunner bootstrapAfterHibernate(DataSource dataSource) {
            return args -> {
                try {
                    String sql = new ClassPathResource("seeddata/migrations/bootstrap_after_hibernate.sql")
                            .getContentAsString(StandardCharsets.UTF_8);
                    new JdbcTemplate(dataSource).execute(sql);
                } catch (java.io.IOException exception) {
                    throw new IllegalStateException("Current PostgreSQL bootstrap SQL is unavailable", exception);
                }
            };
        }
    }

    @Test
    void operatorAcceptDefersPlanningUntilFreshTelemetryPreflight() {
        Order source = orderRepository.findAll().stream().findFirst().orElseThrow();
        Order order = orderRepository.saveAndFlush(copyOrder(source, point(106.70, 10.77)));
        createAcceptedInvoice(order);
        Device device = deviceRepository.findAll().stream().findFirst().orElseThrow();
        device.setStatus(DeviceStatus.AVAILABLE);
        deviceRepository.saveAndFlush(device);

        User manager = createActor(RoleCode.MANAGER);
        User staff = createActor(RoleCode.STAFF);
        String staffId = staff.getId();

        java.time.LocalDate targetDate = order.getPreferredDateFrom() != null ? order.getPreferredDateFrom() : java.time.LocalDate.now(java.time.ZoneOffset.UTC);
        java.time.Instant startAt = targetDate.atTime(9, 0).toInstant(java.time.ZoneOffset.UTC);
        java.time.Instant endAt = targetDate.atTime(11, 0).toInstant(java.time.ZoneOffset.UTC);

        MissionCreateRequest createRequest = new MissionCreateRequest();
        createRequest.setOrderId(order.getId());
        createRequest.setIdempotencyKey(java.util.UUID.randomUUID().toString());
        createRequest.setExpectedPlanRevision(order.getExecutionPlanRevision());
        createRequest.setScopeLabel("E2E acceptance verification");
        createRequest.setScheduledStartAt(startAt);
        createRequest.setScheduledEndAt(endAt);
        authenticate(manager);
        MissionResponse created = missionService.createMission(createRequest);

        OrderChecklistItem checklistItem = new OrderChecklistItem();
        checklistItem.setOrder(order);
        checklistItem.setContent("E2E monitoring requirement");
        checklistItem.setDisplayOrder(0);
        checklistItem.setSourceType(OrderChecklistSourceType.CUSTOMER_CUSTOM);
        checklistItem.setReviewStatus(ChecklistReviewStatus.INCLUDED);
        checklistItem.setEvidencePolicyVersion(2);
        checklistItem.setMinimumEvidenceCount(1);
        checklistItem.setRequiredEvidenceType(RequiredEvidenceType.IMAGE_OR_VIDEO);
        checklistItem = orderChecklistItemRepository.saveAndFlush(checklistItem);

        checklistAssignmentService.replace(created.getId(), new ChecklistAssignmentSelectionRequest(
                order.getExecutionPlanRevision(),
                java.util.List.of(new ChecklistAssignmentSelectionRequest.Item(checklistItem.getId()))));
        executionPlanService.finalizePlan(order.getId(), new ExecutionPlanFinalizeRequest(
                order.getExecutionPlanRevision(), true));

        AssignDeviceRequest deviceRequest = new AssignDeviceRequest();
        deviceRequest.setDeviceId(device.getId());
        MissionResponse deviceAssigned = missionService.assignDevice(created.getId(), deviceRequest);

        MissionResponse staffAssigned = deviceAssigned;
        for (var role : com.ondemandmonitoring.mission.enums.MissionStaffRole.values()) {
            AssignStaffRequest staffRequest = new AssignStaffRequest();
            staffRequest.setStaffId(staffId);
            staffRequest.setAssignedRole(role.name());
            staffAssigned = missionService.assignStaff(staffAssigned.getId(), staffRequest);
        }

        assertThat(staffAssigned.getStatus()).isEqualTo(MissionStatus.WAITING_CREW_CONFIRMATION);

        authenticate(staff);
        MissionResponse accepted = missionService.acceptMission(staffAssigned.getId(), staffId);
        entityManager.flush();
        entityManager.clear();

        assertThat(accepted.getStatus()).isEqualTo(MissionStatus.SCHEDULED);
        assertThat(accepted.getPlan()).isNotNull();
        assertThat(missionPlanRepository.findByMissionId(accepted.getId())).isPresent();
        assertThat(countPlans(accepted.getId())).isEqualTo(1L);
        assertThat(countWaypoints(accepted.getId())).isGreaterThan(0L);
    }

    private User createActor(RoleCode roleCode) {
        User user = new User();
        user.setFullName("E2E " + roleCode);
        user.setEmail("operator-accept-" + roleCode.name().toLowerCase() + "-"
                + java.util.UUID.randomUUID() + "@example.com");
        user.setRole(roleRepository.findByCodeAndActiveTrue(roleCode).orElseThrow());
        user.setIsActive(true);
        return userRepository.saveAndFlush(user);
    }

    private void createAcceptedInvoice(Order order) {
        Instant now = Instant.now();
        BigDecimal total = BigDecimal.valueOf(1_000_000);
        BigDecimal deposit = BigDecimal.valueOf(200_000);

        Quote quote = new Quote();
        quote.setOrder(order);
        quote.setQuoteVersion(1);
        quote.setStatus(QuoteStatus.ACCEPTED_BY_CUSTOMER);
        quote.setPackagePrice(total);
        quote.setAdditionalAmount(BigDecimal.ZERO);
        quote.setDiscountAmount(BigDecimal.ZERO);
        quote.setAdjustmentAmount(BigDecimal.ZERO);
        quote.setTotalAmount(total);
        quote.setAcceptedAt(now);
        quote = quoteRepository.saveAndFlush(quote);

        Invoice invoice = new Invoice();
        invoice.setInvoiceNumber("E2E-" + java.util.UUID.randomUUID());
        invoice.setOrder(order);
        invoice.setQuote(quote);
        invoice.setTotalAmount(total);
        invoice.setDepositAmount(deposit);
        invoice.setPaidAmount(deposit);
        invoice.setRemainingAmount(total.subtract(deposit));
        invoice.setStatus(InvoiceStatus.PARTIALLY_PAID);
        invoice.setIssuedAt(now);
        invoiceRepository.saveAndFlush(invoice);
    }

    private void authenticate(User user) {
        String role = user.getRole().getCode().name();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user.getId(), null,
                        java.util.List.of(new SimpleGrantedAuthority("ROLE_" + role))));
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
