package com.ondemandmonitoring.checklist;

import com.ondemandmonitoring.device.repository.DeviceTelemetryRepository;
import com.ondemandmonitoring.mission.dto.request.AssignDeviceRequest;
import com.ondemandmonitoring.mission.enums.DeviceRole;
import com.ondemandmonitoring.mission.enums.MissionStaffRole;
import com.ondemandmonitoring.mission.enums.MissionStatus;
import com.ondemandmonitoring.mission.repository.DeviceConnectionRepository;
import com.ondemandmonitoring.mission.repository.MissionDeviceAssignmentRepository;
import com.ondemandmonitoring.mission.repository.MissionPlanRepository;
import com.ondemandmonitoring.planning.dto.EnergyEstimate;
import com.ondemandmonitoring.planning.service.MissionEnergyEstimator;
import com.ondemandmonitoring.planning.service.PlanningEnvironment;
import com.ondemandmonitoring.planning.service.RoutePlanner;
import com.ondemandmonitoring.planning.service.SimulationHomeProvider;
import com.ondemandmonitoring.planning.service.impl.MissionPlanningServiceImpl;
import com.ondemandmonitoring.zone.repository.ZoneRepository;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.util.ReflectionTestUtils;

import static com.ondemandmonitoring.checklist.ResourceSafetyPostgresTest.repository;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Real repositories, planning, assignment transactions and PostgreSQL locks in a disposable schema. */
@SpringJUnitConfig(ResourceSafetyPostgresTest.Beans.class)
@EnabledIfEnvironmentVariable(named = "ODMS_CHECKLIST_TEST_JDBC_URL",
        matches = "jdbc:postgresql://127[.]0[.]0[.]1:[0-9]+/odms_checklist_verification")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class MissionDeviceAssignmentInvariantPostgresTest {
    @Autowired ApplicationContext context;
    ResourceSafetyPostgresTest fixture;
    MissionDeviceAssignmentRepository assignments;
    MissionPlanRepository plans;
    MissionPlanningServiceImpl planning;

    @BeforeEach void prepare() {
        // Compose the existing fixture without inheriting its unrelated test methods.
        fixture = new ResourceSafetyPostgresTest();
        context.getAutowireCapableBeanFactory().autowireBean(fixture);
        fixture.prepare();
        fixture.ready(0);
        fixture.planning(fixture.first);
        assignments = repository(fixture.entityManagerFactory, MissionDeviceAssignmentRepository.class);
        plans = repository(fixture.entityManagerFactory, MissionPlanRepository.class);
        var estimator = mock(MissionEnergyEstimator.class);
        when(estimator.estimate(any())).thenReturn(new EnergyEstimate(
                120.0, 100.0, 20.0, 0.0, 500.0, 10.0, 2.0, 5000.0, 20.0, 30.0));
        var route = mock(RoutePlanner.class);
        planning = new MissionPlanningServiceImpl(fixture.missions, plans, assignments,
                repository(fixture.entityManagerFactory, DeviceConnectionRepository.class),
                repository(fixture.entityManagerFactory, DeviceTelemetryRepository.class),
                route, route, route, mock(SimulationHomeProvider.class), mock(PlanningEnvironment.class),
                estimator, repository(fixture.entityManagerFactory, ZoneRepository.class));
        ReflectionTestUtils.setField(fixture.operational, "missionPlanRepository", plans);
        ReflectionTestUtils.setField(fixture.operational, "missionPlanningService", planning);
    }

    @AfterAll void cleanup() { if (fixture != null) fixture.cleanup(); }

    void assign(String device, DeviceRole role) {
        var request = new AssignDeviceRequest();
        request.setDeviceId(device);
        request.setDeviceRole(role);
        fixture.transaction.executeWithoutResult(tx -> fixture.operational.assignDevice(fixture.first, request));
    }

    String currentMain() {
        return fixture.transaction.execute(tx -> assignments
                .findByMissionIdAndDeviceRoleAndIsCurrentTrue(fixture.first, DeviceRole.MAIN)
                .orElseThrow().getDevice().getId());
    }

    int currentMainCount() {
        return fixture.jdbc.queryForObject("select count(*) from mission_device_assignments "
                + "where mission_id=? and is_current=true and device_role='MAIN'", Integer.class, fixture.first);
    }

    String insert(String device, DeviceRole role, boolean current) {
        String id = UUID.randomUUID().toString();
        fixture.jdbc.update("insert into mission_device_assignments "
                + "(id,created_at,version,mission_id,device_id,device_role,checkup_status,is_current) "
                + "values (?,now(),0,?,?,?,'PENDING',?)", id, fixture.first, device, role.name(), current);
        return id;
    }

    void dropInvariantInDisposableSchema() {
        fixture.jdbc.execute("DROP INDEX " + ChecklistPostgresTest.SCHEMA + ".uk_mission_device_assignment_current_main");
    }

    @Test void oneCurrentMainPlansSuccessfully() {
        String main = fixture.device();
        assign(main, DeviceRole.MAIN);
        assertEquals(main, currentMain());
        var plan = fixture.transaction.execute(tx -> planning.generateGpsDirectPlan(fixture.first));
        assertNotNull(plan.getId());
        assertEquals(1, currentMainCount());
    }

    @Test void mainAndSupportAreValidAndBatteryUsesOnlyMainTelemetry() {
        String main = fixture.device(), support = fixture.device(), pilot = fixture.staff();
        assign(main, DeviceRole.MAIN);
        assign(support, DeviceRole.SUPPORT);
        String deviceAssignment = fixture.jdbc.queryForObject(
                "select id from mission_device_assignments where mission_id=? and device_id=?", String.class, fixture.first, main);
        String staffAssignment = UUID.randomUUID().toString(), connection = UUID.randomUUID().toString();
        fixture.jdbc.update("insert into mission_staff_assignments "
                + "(id,created_at,version,mission_id,staff_id,assigned_role,response_status,is_current) "
                + "values (?,now(),0,?,?,'PILOT','ACCEPTED',true)", staffAssignment, fixture.first, pilot);
        fixture.jdbc.update("insert into device_connections "
                + "(id,created_at,version,mission_id,mission_device_assignment_id,mission_staff_assignment_id,connection_status,telemetry_active,connected_at) "
                + "values (?,now(),0,?,?,?,'CONNECTED',true,now())", connection, fixture.first, deviceAssignment, staffAssignment);
        fixture.jdbc.update("insert into device_telemetry "
                + "(id,created_at,version,device_connection_id,connected,recorded_at,battery_percent) "
                + "values (?,now(),0,?,true,now(),72.5)", UUID.randomUUID().toString(), connection);
        var plan = fixture.transaction.execute(tx -> planning.generateGpsDirectPlan(fixture.first));
        assertEquals(72.5, plan.getAvailableBatteryPercentAtPlanning());
        assertEquals(main, currentMain());
        assertEquals(2, fixture.rows("mission_device_assignments"));
    }

    @Test void replacingCurrentMainPreservesHistorySupportAndIdempotentReplay() {
        String old = fixture.device(), next = fixture.device(), support = fixture.device();
        assign(old, DeviceRole.MAIN);
        assign(support, DeviceRole.SUPPORT);
        assign(next, DeviceRole.MAIN);
        assertEquals(next, currentMain());
        assertEquals(1, currentMainCount());
        var history = fixture.jdbc.queryForMap("select is_current,released_at,release_reason from mission_device_assignments "
                + "where mission_id=? and device_id=?", fixture.first, old);
        assertEquals(false, history.get("is_current"));
        assertNotNull(history.get("released_at"));
        assertNotNull(history.get("release_reason"));
        assertTrue(fixture.jdbc.queryForObject("select is_current from mission_device_assignments where mission_id=? and device_id=?",
                Boolean.class, fixture.first, support));
        assign(next, DeviceRole.MAIN);
        assertEquals(3, fixture.rows("mission_device_assignments"));
        assertEquals(history, fixture.jdbc.queryForMap("select is_current,released_at,release_reason from mission_device_assignments "
                + "where mission_id=? and device_id=?", fixture.first, old));
    }

    @Test void corruptCurrentMainFailsStrictQueryAndVerificationWithoutDeletingHistory() {
        dropInvariantInDisposableSchema();
        insert(fixture.device(), DeviceRole.MAIN, true);
        insert(fixture.device(), DeviceRole.MAIN, true);
        assertNonUnique(assertThrows(RuntimeException.class, this::currentMain));
        var before = fixture.jdbc.queryForList("select * from mission_device_assignments order by id");
        var failure = assertThrows(org.springframework.dao.DataAccessException.class, fixture::verifyCurrentSchema);
        assertTrue(failure.getMostSpecificCause().getMessage().contains("uk_mission_device_assignment_current_main"));
        assertEquals(before, fixture.jdbc.queryForList("select * from mission_device_assignments order by id"));
    }

    @Test void databaseRejectsDuplicateCurrentMainButAllowsHistoricalAndSupportRows() {
        insert(fixture.device(), DeviceRole.MAIN, true);
        assertThrows(DataIntegrityViolationException.class, () -> insert(fixture.device(), DeviceRole.MAIN, true));
        insert(fixture.device(), DeviceRole.MAIN, false);
        insert(fixture.device(), DeviceRole.MAIN, false);
        insert(fixture.device(), DeviceRole.SUPPORT, true);
        insert(fixture.device(), DeviceRole.SUPPORT, true);
        assertEquals(1, currentMainCount());
        assertEquals(5, fixture.rows("mission_device_assignments"));
    }

    @Test void concurrentReplacementSerializesAndLeavesExactlyOneCurrentMain() throws Exception {
        String old = fixture.device(), a = fixture.device(), b = fixture.device(), support = fixture.device();
        assign(old, DeviceRole.MAIN);
        assign(support, DeviceRole.SUPPORT);
        fixture.race(() -> { assign(a, DeviceRole.MAIN); return true; },
                () -> { assign(b, DeviceRole.MAIN); return true; }).forEach(outcome -> assertEquals(true, outcome));
        assertEquals(1, currentMainCount());
        assertTrue(java.util.Set.of(a, b).contains(currentMain()));
        assertEquals(4, fixture.rows("mission_device_assignments"));
        assertEquals(2, fixture.jdbc.queryForObject("select count(*) from mission_device_assignments "
                + "where mission_id=? and device_role='MAIN' and is_current=false and released_at is not null", Integer.class, fixture.first));
        assertEquals(2, fixture.jdbc.queryForObject("select count(*) from resource_time_locks "
                + "where mission_id=? and released_at is null", Integer.class, fixture.first));
    }

    void pendingCrew() {
        String staff = fixture.staff();
        for (MissionStaffRole role : MissionStaffRole.values()) {
            fixture.jdbc.update("insert into mission_staff_assignments "
                    + "(id,created_at,version,mission_id,staff_id,assigned_role,response_status,is_current) "
                    + "values (?,now(),0,?,?,?, ?,true)", UUID.randomUUID().toString(), fixture.first, staff, role.name(),
                    role == MissionStaffRole.INSPECTOR ? "PENDING" : "ACCEPTED");
        }
        fixture.actor(staff);
        fixture.jdbc.update("update missions set status='WAITING_CREW_CONFIRMATION' where id=?", fixture.first);
    }

    @Test void acceptCurrentWithMainAndSupportSucceedsAndGeneratesPlanForExactMission() {
        assign(fixture.device(), DeviceRole.MAIN);
        assign(fixture.device(), DeviceRole.SUPPORT);
        pendingCrew();
        var response = fixture.transaction.execute(tx -> fixture.operational.acceptCurrentStaffMission(fixture.first));
        assertEquals(fixture.first, response.getId());
        assertEquals(MissionStatus.SCHEDULED, response.getStatus());
        assertTrue(plans.findByMissionId(fixture.first).isPresent());
        assertFalse(plans.findByMissionId(fixture.second).isPresent());
        assertEquals(2, fixture.rows("mission_device_assignments"));
    }

    @Test void duplicateMainMakesAcceptanceFailAndRollsBackCrewResponse() {
        dropInvariantInDisposableSchema();
        insert(fixture.device(), DeviceRole.MAIN, true);
        insert(fixture.device(), DeviceRole.MAIN, true);
        pendingCrew();
        assertNonUnique(assertThrows(RuntimeException.class,
                () -> fixture.transaction.execute(tx -> fixture.operational.acceptCurrentStaffMission(fixture.first))));
        assertEquals("WAITING_CREW_CONFIRMATION", fixture.jdbc.queryForObject("select status from missions where id=?", String.class, fixture.first));
        assertEquals("PENDING", fixture.jdbc.queryForObject("select response_status from mission_staff_assignments "
                + "where mission_id=? and assigned_role='INSPECTOR'", String.class, fixture.first));
        assertFalse(plans.findByMissionId(fixture.first).isPresent());
    }

    void assertNonUnique(RuntimeException failure) {
        Throwable cause = failure;
        while (cause.getCause() != null) cause = cause.getCause();
        assertTrue(failure instanceof org.springframework.dao.IncorrectResultSizeDataAccessException
                || cause instanceof org.hibernate.NonUniqueResultException, failure.toString());
    }
}
