package com.ondemandmonitoring.mission.service;

import com.ondemandmonitoring.common.exception.ApiException;
import com.ondemandmonitoring.device.domain.Device;
import com.ondemandmonitoring.devicecheck.domain.PersistedPostDeviceCheck;
import com.ondemandmonitoring.device.enums.DeviceStatus;
import com.ondemandmonitoring.device.repository.DeviceRepository;
import com.ondemandmonitoring.device.repository.MaintenanceTicketRepository;
import com.ondemandmonitoring.devicecheck.repository.PersistedPostDeviceCheckRepository;
import com.ondemandmonitoring.devicecheck.service.IPreDeviceCheckCompletionService;
import com.ondemandmonitoring.devicecheck.service.IPersistedPostDeviceCheckService;
import com.ondemandmonitoring.mission.domain.DeviceConnection;
import com.ondemandmonitoring.mission.domain.FlightToken;
import com.ondemandmonitoring.mission.domain.Mission;
import com.ondemandmonitoring.mission.domain.MissionDeviceAssignment;
import com.ondemandmonitoring.mission.domain.MissionPlan;
import com.ondemandmonitoring.mission.domain.MissionStaffAssignment;
import com.ondemandmonitoring.mission.dto.request.AssignDeviceRequest;
import com.ondemandmonitoring.mission.dto.request.AssignStaffRequest;
import com.ondemandmonitoring.mission.dto.response.FlightTokenResponse;
import com.ondemandmonitoring.mission.dto.response.MissionResponse;
import com.ondemandmonitoring.mission.enums.FeasibilityStatus;
import com.ondemandmonitoring.mission.enums.MissionStaffRole;
import com.ondemandmonitoring.mission.enums.MissionStatus;
import com.ondemandmonitoring.mission.enums.PlanningAlgorithm;
import com.ondemandmonitoring.mission.enums.StaffResponseStatus;
import com.ondemandmonitoring.mission.mapper.FlightTokenMapper;
import com.ondemandmonitoring.mission.mapper.MissionMapper;
import com.ondemandmonitoring.mission.repository.ControlHandoverRepository;
import com.ondemandmonitoring.mission.repository.DeviceConnectionRepository;
import com.ondemandmonitoring.mission.repository.FlightTokenRepository;
import com.ondemandmonitoring.mission.repository.MissionDeviceAssignmentRepository;
import com.ondemandmonitoring.mission.repository.MissionPlanRepository;
import com.ondemandmonitoring.mission.repository.MissionRepository;
import com.ondemandmonitoring.mission.repository.MissionRescheduleHistoryRepository;
import com.ondemandmonitoring.mission.repository.MissionStaffAssignmentRepository;
import com.ondemandmonitoring.mission.repository.ResourceTimeLockRepository;
import com.ondemandmonitoring.mission.service.impl.DeviceConnectionService;
import com.ondemandmonitoring.mission.service.impl.FlightTokenService;
import com.ondemandmonitoring.mission.service.impl.MissionService;
import com.ondemandmonitoring.order.repository.OrderRepository;
import com.ondemandmonitoring.planning.service.MissionPlanningService;
import com.ondemandmonitoring.role.domain.Role;
import com.ondemandmonitoring.role.domain.RoleCode;
import com.ondemandmonitoring.user.domain.User;
import com.ondemandmonitoring.user.service.IStaffDirectoryService;
import com.ondemandmonitoring.userschedule.repository.UserScheduleRepository;
import com.ondemandmonitoring.user.service.AuthenticatedUserResolver;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class MissionServiceTest {

    MissionRescheduleHistoryRepository missionRescheduleHistoryRepository;
    MissionRepository missionRepository;
    DeviceRepository deviceRepository;
    FlightTokenRepository flightTokenRepository;
    MissionMapper missionMapper;
    FlightTokenMapper flightTokenMapper;

    MissionPlanRepository missionPlanRepository;
    MissionPlanningService missionPlanningService;
    DeviceConnectionRepository deviceConnectionRepository;
    ControlHandoverRepository controlHandoverRepository;
    PersistedPostDeviceCheckRepository postDeviceCheckRepository;
    MaintenanceTicketRepository maintenanceTicketRepository;
    OrderRepository orderRepository;
    IDeviceConnectionService deviceConnectionService;
    IFlightTokenService flightTokenService;
    IMissionResultService missionResultService;
    IPreDeviceCheckCompletionService preDeviceCheckCompletionService;
    IPersistedPostDeviceCheckService persistedPostDeviceCheckService;
    IStaffDirectoryService staffDirectory;
    AuthenticatedUserResolver authenticatedUserResolver;
    UserScheduleRepository userScheduleRepository;
    MissionDeviceAssignmentRepository missionDeviceAssignmentRepository;
    MissionStaffAssignmentRepository missionStaffAssignmentRepository;
    ResourceTimeLockRepository resourceTimeLockRepository;

    MissionService missionService;

    @BeforeEach
    void setUp() {
        missionRescheduleHistoryRepository = mock(MissionRescheduleHistoryRepository.class);
        missionRepository = mock(MissionRepository.class);
        deviceRepository = mock(DeviceRepository.class);
        flightTokenRepository = mock(FlightTokenRepository.class);
        missionMapper = mock(MissionMapper.class);
        flightTokenMapper = mock(FlightTokenMapper.class);
        missionPlanRepository = mock(MissionPlanRepository.class);
        missionPlanningService = mock(MissionPlanningService.class);
        deviceConnectionRepository = mock(DeviceConnectionRepository.class);
        controlHandoverRepository = mock(ControlHandoverRepository.class);
        postDeviceCheckRepository = mock(PersistedPostDeviceCheckRepository.class);
        maintenanceTicketRepository = mock(MaintenanceTicketRepository.class);
        orderRepository = mock(OrderRepository.class);
        staffDirectory = mock(IStaffDirectoryService.class);
        authenticatedUserResolver = mock(AuthenticatedUserResolver.class);
        userScheduleRepository = mock(UserScheduleRepository.class);
        missionDeviceAssignmentRepository = mock(MissionDeviceAssignmentRepository.class);
        missionStaffAssignmentRepository = mock(MissionStaffAssignmentRepository.class);
        resourceTimeLockRepository = mock(ResourceTimeLockRepository.class);
        missionResultService = mock(IMissionResultService.class);
        preDeviceCheckCompletionService = mock(IPreDeviceCheckCompletionService.class);
        persistedPostDeviceCheckService = mock(IPersistedPostDeviceCheckService.class);

        deviceConnectionService = new DeviceConnectionService(
                missionRepository,
                deviceConnectionRepository,
                missionDeviceAssignmentRepository,
                missionStaffAssignmentRepository,
                deviceRepository,
                missionMapper);

        flightTokenService = new FlightTokenService(
                flightTokenRepository,
                missionDeviceAssignmentRepository,
                missionStaffAssignmentRepository);

        missionService = new MissionService(
                missionRescheduleHistoryRepository,
                missionRepository,
                deviceRepository,
                flightTokenRepository,
                missionMapper,
                flightTokenMapper,
                missionPlanRepository,
                missionPlanningService,
                deviceConnectionRepository,
                controlHandoverRepository,
                postDeviceCheckRepository,
                maintenanceTicketRepository,
                orderRepository,
                deviceConnectionService,
                flightTokenService,
                missionResultService,
                preDeviceCheckCompletionService,
                persistedPostDeviceCheckService,
                staffDirectory,
                authenticatedUserResolver,
                userScheduleRepository,
                missionDeviceAssignmentRepository,
                missionStaffAssignmentRepository,
                resourceTimeLockRepository);

        when(missionMapper.toResponse(any())).thenAnswer(inv -> {
            Mission m = inv.getArgument(0);
            if (m == null)
                return null;
            return MissionResponse.builder()
                    .id(m.getId())
                    .missionCode(m.getMissionCode())
                    .status(m.getStatus())
                    .actualStartAt(m.getActualStartAt())
                    .actualEndAt(m.getActualEndAt())
                    .build();
        });

        when(flightTokenMapper.toResponse(any())).thenAnswer(inv -> {
            FlightToken ft = inv.getArgument(0);
            if (ft == null)
                return null;
            return FlightTokenResponse.builder()
                    .tokenValue(ft.getTokenValue())
                    .issuedAt(ft.getIssuedAt())
                    .expiresAt(ft.getExpiresAt())
                    .build();
        });
    }

    @Test
    void searchStaffMissionsMapsRepositoryPage() {
        Mission mission = new Mission();
        mission.setMissionCode("MS-001");
        mission.setStatus(MissionStatus.SCHEDULED);
        PageRequest pageable = PageRequest.of(0, 20);
        when(missionRepository.findAll(any(Specification.class), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(mission), pageable, 1));

        var result = missionService.searchStaffMissions(MissionStatus.SCHEDULED,
                Instant.parse("2026-09-20T17:00:00Z"),
                Instant.parse("2026-09-27T17:00:00Z"), pageable);

        assertThat(result.getItems()).hasSize(1);
        assertThat(result.getItems().get(0).getMissionCode()).isEqualTo("MS-001");
        assertThat(result.getTotalItems()).isEqualTo(1);
    }

    // =========================================================================
    // F3.1 – Staff Acceptance / Rejection
    // =========================================================================
    @Nested
    @DisplayName("F3.1 - Staff Acceptance & Rejection")
    class F3_1_OperatorAcceptanceAndRejection {

        @Test
        void assignStaffWithoutTelemetryDefersPlanning() {
            Mission mission = buildMission("m-assign", MissionStatus.RESOURCE_ASSIGNING);
            mission.setScheduledStartAt(Instant.now().plusSeconds(3600));
            mission.setScheduledEndAt(Instant.now().plusSeconds(7200));

            User staffUser = new User();
            staffUser.setId("op-01");
            staffUser.setIsActive(true);
            Role role = new Role();
            role.setCode(RoleCode.STAFF);
            role.setActive(true);
            staffUser.setRole(role);

            when(missionRepository.findById("m-assign")).thenReturn(Optional.of(mission));
            when(staffDirectory.requireActiveStaff("op-01")).thenReturn(staffUser);
            when(missionDeviceAssignmentRepository.existsByMissionId("m-assign")).thenReturn(true);
            when(missionStaffAssignmentRepository.findAllByMissionIdAndIsCurrentTrue("m-assign"))
                    .thenReturn(List.of(staffAssignment(mission, "op-01", "PENDING")));
            when(missionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            AssignStaffRequest request = AssignStaffRequest.builder().staffId("op-01").build();
            assertThat(missionService.assignStaff("m-assign", request).getStatus())
                    .isEqualTo(MissionStatus.WAITING_CREW_CONFIRMATION);
            verifyNoInteractions(missionPlanningService);
        }

        @Test
        @DisplayName("1. acceptMission success when WAITING_OPERATOR_ACCEPTANCE")
        void acceptMission_success() {
            Mission mission = buildMission("m-1", MissionStatus.WAITING_OPERATOR_ACCEPTANCE);
            when(missionRepository.findById("m-1")).thenReturn(Optional.of(mission));
            MissionStaffAssignment assignment = staffAssignment(mission, "op-01", "PENDING");
            when(missionStaffAssignmentRepository.findAllByMissionIdAndStaffIdAndIsCurrentTrueOrderByAssignedAtDesc("m-1", "op-01"))
                    .thenReturn(List.of(assignment));
            when(missionStaffAssignmentRepository.findAllByMissionIdAndIsCurrentTrue("m-1"))
                    .thenReturn(List.of(assignment));
            when(missionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            MissionResponse result = missionService.acceptMission("m-1", "op-01");

            assertThat(result.getStatus()).isEqualTo(MissionStatus.SCHEDULED);
            verify(missionStaffAssignmentRepository).saveAll(any());
        }

        @Test
        @DisplayName("2. acceptMission throws exception when mission not found")
        void acceptMission_notFound_throws() {
            when(missionRepository.findById("m-missing")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> missionService.acceptMission("m-missing", "op-01"))
                    .isInstanceOf(ApiException.class)
                    .hasMessageContaining("Mission not found");
        }

        @Test
        @DisplayName("3. acceptMission throws exception when status is invalid")
        void acceptMission_wrongStatus_throws() {
            Mission mission = buildMission("m-1", MissionStatus.SCHEDULED);
            when(missionRepository.findById("m-1")).thenReturn(Optional.of(mission));

            assertThatThrownBy(() -> missionService.acceptMission("m-1", "op-01"))
                    .isInstanceOf(ApiException.class)
                    .hasMessageContaining("waiting for crew confirmation");
        }

        @Test
        @DisplayName("4. acceptMission rejects wrong staff before planning")
        void acceptMission_wrongOperator_doesNotPlan() {
            Mission mission = buildMission("m-operator", MissionStatus.WAITING_OPERATOR_ACCEPTANCE);
            when(missionRepository.findById("m-operator")).thenReturn(Optional.of(mission));

            assertThatThrownBy(() -> missionService.acceptMission("m-operator", "op-other"))
                    .isInstanceOf(ApiException.class)
                    .hasMessageContaining("Staff is not assigned to mission");
        }

        @Test
        @DisplayName("8. rejectMission success when WAITING_OPERATOR_ACCEPTANCE")
        void rejectMission_success() {
            Mission mission = buildMission("m-2", MissionStatus.WAITING_OPERATOR_ACCEPTANCE);
            MissionStaffAssignment assignment = staffAssignment(mission, "op-01", "PENDING");
            when(missionStaffAssignmentRepository.findAllByMissionIdAndStaffIdAndIsCurrentTrueOrderByAssignedAtDesc("m-2", "op-01"))
                    .thenReturn(List.of(assignment));
            when(missionRepository.findById("m-2")).thenReturn(Optional.of(mission));
            when(missionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            MissionResponse result = missionService.rejectMission("m-2", "op-01", "Trùng lịch cá nhân");

            assertThat(result.getStatus()).isEqualTo(MissionStatus.RESOURCE_ASSIGNING);
            verify(missionStaffAssignmentRepository).saveAll(any());
        }

        @Test
        @DisplayName("9. rejectMission throws exception when mission not found")
        void rejectMission_notFound_throws() {
            when(missionRepository.findById("m-missing")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> missionService.rejectMission("m-missing", "op-01", "Bận"))
                    .isInstanceOf(ApiException.class)
                    .hasMessageContaining("Mission not found");
        }

        @Test
        @DisplayName("10. rejectMission throws exception when status is invalid")
        void rejectMission_wrongStatus_throws() {
            Mission mission = buildMission("m-2", MissionStatus.IN_FLIGHT);
            when(missionRepository.findById("m-2")).thenReturn(Optional.of(mission));

            assertThatThrownBy(() -> missionService.rejectMission("m-2", "op-01", "Trùng lịch"))
                    .isInstanceOf(ApiException.class)
                    .hasMessageContaining("waiting for crew confirmation");
        }
    }

    // =========================================================================
    // F3.2 – GCS Connect & Digital Pre-flight Gate
    // =========================================================================
    @Nested
    @DisplayName("F3.2 - GCS Connect & Pre-flight Gate")
    class F3_2_GcsConnectAndPreflightGate {

        @Test
        @DisplayName("1. connectGcs success from SCHEDULED status")
        void connectGcs_success_fromScheduled() {
            Mission mission = buildMission("m-gcs", MissionStatus.SCHEDULED);
            Device device = buildDevice("DEV-01", DeviceStatus.AVAILABLE);
            MissionDeviceAssignment mda = new MissionDeviceAssignment();
            mda.setDevice(device);
            mda.setIsCurrent(true);

            User staff = new User();
            staff.setId("staff-01");
            MissionStaffAssignment msa = new MissionStaffAssignment();
            msa.setStaff(staff);
            msa.setIsCurrent(true);
            msa.setAssignedRole(MissionStaffRole.OPERATOR);
            msa.setResponseStatus(StaffResponseStatus.ACCEPTED);

            when(missionRepository.findById("m-gcs")).thenReturn(Optional.of(mission));
            when(missionDeviceAssignmentRepository.findAllByMissionIdAndIsCurrentTrueOrderByCreatedAtDesc("m-gcs"))
                    .thenReturn(List.of(mda));
            when(missionStaffAssignmentRepository.findAllByMissionIdAndIsCurrentTrue("m-gcs"))
                    .thenReturn(List.of(msa));
            when(deviceConnectionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(missionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(deviceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            MissionResponse result = missionService.connectGcs("m-gcs");

            assertThat(result.getStatus()).isEqualTo(MissionStatus.CONNECTED);
            assertThat(device.getStatus()).isEqualTo(DeviceStatus.PREFLIGHT);
        }

        @Test
        @DisplayName("2. connectGcs success from CONNECTED status")
        void connectGcs_success_fromConnected() {
            Mission mission = buildMission("m-gcs2", MissionStatus.CONNECTED);
            Device device = buildDevice("DEV-02", DeviceStatus.PREFLIGHT);
            MissionDeviceAssignment deviceAssignment = new MissionDeviceAssignment();
            deviceAssignment.setDevice(device);
            deviceAssignment.setIsCurrent(true);
            when(missionDeviceAssignmentRepository.findAllByMissionIdAndIsCurrentTrueOrderByCreatedAtDesc("m-gcs2"))
                    .thenReturn(List.of(deviceAssignment));
            when(missionStaffAssignmentRepository.findAllByMissionIdAndIsCurrentTrue("m-gcs2"))
                    .thenReturn(List.of(staffAssignment(mission, "staff-01", MissionStaffRole.OPERATOR, "ACCEPTED")));
            when(deviceConnectionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(missionRepository.findById("m-gcs2")).thenReturn(Optional.of(mission));
            when(missionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            MissionResponse result = missionService.connectGcs("m-gcs2");

            assertThat(result.getStatus()).isEqualTo(MissionStatus.CONNECTED);
        }

        @Test
        @DisplayName("3. connectGcs throws exception when status is invalid (e.g. IN_FLIGHT)")
        void connectGcs_invalidStatus_throws() {
            Mission mission = buildMission("m-gcs3", MissionStatus.IN_FLIGHT);
            when(missionRepository.findById("m-gcs3")).thenReturn(Optional.of(mission));

            assertThatThrownBy(() -> missionService.connectGcs("m-gcs3"))
                    .isInstanceOf(ApiException.class)
                    .hasMessageContaining("SCHEDULED state");
        }

        @Test
        @DisplayName("3b. disconnectGcs updates active GCS session status to DISCONNECTED")
        void disconnectGcs_success_updatesSessionToDisconnected() {
            Mission mission = buildMission("m-disc", MissionStatus.CONNECTED);
            DeviceConnection session = new DeviceConnection();
            session.setConnectionStatus("CONNECTED");
            session.setTelemetryActive(true);

            when(missionRepository.findById("m-disc")).thenReturn(Optional.of(mission));
            when(deviceConnectionRepository.findTopByMissionIdAndConnectionStatusOrderByConnectedAtDesc("m-disc",
                    "CONNECTED"))
                    .thenReturn(Optional.of(session));
            when(deviceConnectionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            MissionResponse response = missionService.disconnectGcs("m-disc", "OPERATOR_EXIT");

            assertThat(response).isNotNull();
            assertThat(session.getConnectionStatus()).isEqualTo("DISCONNECTED");
            assertThat(session.getTelemetryActive()).isFalse();
            assertThat(session.getDisconnectReason()).isEqualTo("OPERATOR_EXIT");
        }

        @Test
        @DisplayName("3c. handleGcsSessionLost updates GCS session status to LOST and triggers RTL (RETURNING)")
        void handleGcsSessionLost_updatesSessionToLost_andTriggersRTL() {
            Mission mission = buildMission("m-lost", MissionStatus.IN_FLIGHT);
            Device device = buildDevice("DEV-01", DeviceStatus.ACTIVE_MISSION);
            MissionDeviceAssignment mda = new MissionDeviceAssignment();
            mda.setDevice(device);
            mda.setIsCurrent(true);
            when(missionDeviceAssignmentRepository.findAllByMissionIdAndIsCurrentTrueOrderByCreatedAtDesc("m-lost"))
                    .thenReturn(List.of(mda));

            DeviceConnection session = new DeviceConnection();
            session.setConnectionStatus("CONNECTED");
            session.setTelemetryActive(true);

            when(missionRepository.findById("m-lost")).thenReturn(Optional.of(mission));
            when(deviceConnectionRepository.findTopByMissionIdAndConnectionStatusOrderByConnectedAtDesc("m-lost",
                    "CONNECTED"))
                    .thenReturn(Optional.of(session));
            when(deviceConnectionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(missionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(deviceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            MissionResponse response = missionService.handleGcsSessionLost("m-lost", "SIGNAL_LOSS");

            assertThat(response).isNotNull();
            assertThat(session.getConnectionStatus()).isEqualTo("LOST");
            assertThat(session.getTelemetryActive()).isFalse();
            assertThat(session.getDisconnectReason()).isEqualTo("SIGNAL_LOSS");
            assertThat(mission.getStatus()).isEqualTo(MissionStatus.RETURNING);
            assertThat(device.getStatus()).isEqualTo(DeviceStatus.RETURNING);
        }

        @Test
        void telemetryReadinessUsesCurrentMissionDevice() {
            Mission mission = buildMission("m-ready", MissionStatus.CONNECTED);
            Device device = buildDevice("DEV-01", DeviceStatus.PREFLIGHT);
            device.setDeviceCode("DEV-01");
            MissionDeviceAssignment assignment = new MissionDeviceAssignment();
            assignment.setDevice(device);

            when(missionRepository.findById("m-ready")).thenReturn(Optional.of(mission));
            when(missionDeviceAssignmentRepository.findFirstByMissionIdOrderByCreatedAtDesc("m-ready"))
                    .thenReturn(Optional.of(assignment));

            var readiness = missionService.getTelemetryReadiness("m-ready");

            assertThat(readiness.deviceId()).isEqualTo("DEV-01");
        }
    }

    // =========================================================================
    // Control Handover
    // =========================================================================
    @Nested
    @DisplayName("Control Handover")
    class ControlHandoverTests {

        @Test
        @DisplayName("5. handoverControl success when READY_TO_FLY")
        void handoverControl_success() {
            Mission mission = buildMission("m-5", MissionStatus.READY_TO_FLY);
            User staff = new User();
            staff.setId("op-new");
            MissionStaffAssignment msa = new MissionStaffAssignment();
            msa.setStaff(staff);
            msa.setIsCurrent(true);

            when(missionRepository.findById("m-5")).thenReturn(Optional.of(mission));
            msa.setAssignedRole(MissionStaffRole.OPERATOR);
            msa.setResponseStatus(StaffResponseStatus.ACCEPTED);
            when(missionStaffAssignmentRepository.findAllByMissionIdAndStaffIdAndIsCurrentTrueOrderByAssignedAtDesc("m-5", "op-new"))
                    .thenReturn(List.of(msa));
            when(missionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            MissionResponse result = missionService.handoverControl("m-5", "op-new");

            verify(controlHandoverRepository, atLeastOnce()).save(any());
        }

        @Test
        @DisplayName("6. handoverControl throws when status is not READY_TO_FLY")
        void handoverControl_invalidStatus_throws() {
            Mission mission = buildMission("m-5", MissionStatus.SCHEDULED);
            when(missionRepository.findById("m-5")).thenReturn(Optional.of(mission));

            assertThatThrownBy(() -> missionService.handoverControl("m-5", "op-new"))
                    .isInstanceOf(ApiException.class)
                    .hasMessageContaining("must be READY_TO_FLY");
        }
    }

    // =========================================================================
    // F3.3 – Takeoff & Execution Lifecycle
    // =========================================================================
    @Nested
    @DisplayName("F3.3 - Takeoff & Flight Lifecycle")
    class F3_3_TakeoffAndFlightLifecycle {

        @Test
        @DisplayName("1. startMission success with valid FlightToken")
        void startMission_success_withValidToken() {
            Mission mission = buildMission("m-7", MissionStatus.READY_TO_FLY);
            Device device = buildDevice("DEV-01", DeviceStatus.PREFLIGHT);
            MissionDeviceAssignment mda = new MissionDeviceAssignment();
            mda.setDevice(device);
            when(missionDeviceAssignmentRepository.findFirstByMissionIdOrderByCreatedAtDesc("m-7"))
                    .thenReturn(Optional.of(mda));

            FlightToken token = new FlightToken();
            token.setTokenValue("valid-token");
            token.setMissionId("m-7");
            token.setExpiresAt(Instant.now().plusSeconds(600));
            token.setUsed(false);
            token.setRevoked(false);

            when(missionRepository.findById("m-7")).thenReturn(Optional.of(mission));
            when(flightTokenRepository.findByTokenValue("valid-token")).thenReturn(Optional.of(token));
            when(missionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(deviceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            MissionResponse result = missionService.startMission("m-7", "valid-token");

            assertThat(result.getStatus()).isEqualTo(MissionStatus.IN_FLIGHT);
            assertThat(device.getStatus()).isEqualTo(DeviceStatus.ACTIVE_MISSION);
            assertThat(token.isUsed()).isTrue();
        }

        @Test
        @DisplayName("2. startMission throws and revokes token when FlightToken is expired")
        void startMission_expiredToken_revokesToken_throws() {
            Mission mission = buildMission("m-7", MissionStatus.READY_TO_FLY);

            FlightToken expiredToken = new FlightToken();
            expiredToken.setTokenValue("expired-token");
            expiredToken.setMissionId("m-7");
            expiredToken.setExpiresAt(Instant.now().minusSeconds(10));
            expiredToken.setUsed(false);
            expiredToken.setRevoked(false);

            when(missionRepository.findById("m-7")).thenReturn(Optional.of(mission));
            when(flightTokenRepository.findByTokenValue("expired-token")).thenReturn(Optional.of(expiredToken));

            assertThatThrownBy(() -> missionService.startMission("m-7", "expired-token"))
                    .isInstanceOf(ApiException.class)
                    .hasMessageContaining("expired or revoked");

            assertThat(expiredToken.isRevoked()).isTrue();
        }

        @Test
        @DisplayName("3. startMission throws exception when provided FlightToken does not exist")
        void startMission_tokenNotFound_throws() {
            Mission mission = buildMission("m-7", MissionStatus.READY_TO_FLY);
            when(missionRepository.findById("m-7")).thenReturn(Optional.of(mission));
            when(flightTokenRepository.findByTokenValue("invalid-token")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> missionService.startMission("m-7", "invalid-token"))
                    .isInstanceOf(ApiException.class)
                    .hasMessageContaining("Invalid flight access token");
        }

        @Test
        @DisplayName("4. startMission without explicit token param auto-resolves valid un-used token for mission")
        void startMission_withoutToken_success() {
            Mission mission = buildMission("m-7", MissionStatus.READY_TO_FLY);
            Device device = buildDevice("DEV-01", DeviceStatus.PREFLIGHT);
            MissionDeviceAssignment mda = new MissionDeviceAssignment();
            mda.setDevice(device);
            when(missionDeviceAssignmentRepository.findFirstByMissionIdOrderByCreatedAtDesc("m-7"))
                    .thenReturn(Optional.of(mda));

            FlightToken token = new FlightToken();
            token.setTokenValue("auto-token");
            token.setExpiresAt(Instant.now().plusSeconds(600));
            token.setUsed(false);
            token.setRevoked(false);

            when(missionRepository.findById("m-7")).thenReturn(Optional.of(mission));
            when(flightTokenRepository.findAllByMissionIdAndUsedFalseAndRevokedFalseOrderByIssuedAtDesc("m-7"))
                    .thenReturn(List.of(token));
            when(missionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(deviceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            MissionResponse result = missionService.startMission("m-7");

            assertThat(result.getStatus()).isEqualTo(MissionStatus.IN_FLIGHT);
            assertThat(token.isUsed()).isTrue();
        }
    }

    // =========================================================================
    // F3.4 & F3.5 - Postflight, Completion & Failure
    // =========================================================================
    @Nested
    @DisplayName("F3.4 & F3.5 - Postflight, Completion & Failure")
    class F3_4_And_F3_5_PostflightCompletionAndFailure {

        @Test
        @DisplayName("1. startPostflightChecking success when RETURNING")
        void startPostflightChecking_success() {
            Mission mission = buildMission("m-pf", MissionStatus.RETURNING);
            when(missionRepository.findById("m-pf")).thenReturn(Optional.of(mission));
            when(missionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            MissionResponse result = missionService.startPostDeviceChecking("m-pf");

            assertThat(result.getStatus()).isEqualTo(MissionStatus.POSTFLIGHT_CHECKING);
        }

        @Test
        @DisplayName("2. startPostflightChecking throws exception when status is not RETURNING")
        void startPostflightChecking_invalidStatus_throws() {
            Mission mission = buildMission("m-pf", MissionStatus.IN_FLIGHT);
            when(missionRepository.findById("m-pf")).thenReturn(Optional.of(mission));

            assertThatThrownBy(() -> missionService.startPostDeviceChecking("m-pf"))
                    .isInstanceOf(ApiException.class)
                    .hasMessageContaining("must be RETURNING");
        }

        @Test
        @DisplayName("3. completeMission success after postcheck is ready for review")
        void completeMission_success() {
            Mission mission = buildMission("m-8", MissionStatus.PENDING_REVIEW);
            Device device = buildDevice("DEV-01", DeviceStatus.RETURNING);
            MissionDeviceAssignment mda = new MissionDeviceAssignment();
            mda.setDevice(device);
            when(missionDeviceAssignmentRepository.findFirstByMissionIdOrderByCreatedAtDesc("m-8"))
                    .thenReturn(Optional.of(mda));

            when(missionRepository.findById("m-8")).thenReturn(Optional.of(mission));
            when(postDeviceCheckRepository.findFirstByMissionIdOrderByCreatedAtDesc("m-8"))
                    .thenReturn(Optional.of(new PersistedPostDeviceCheck()));
            when(missionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(deviceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            MissionResponse result = missionService.completeMission("m-8");

            assertThat(result.getStatus()).isEqualTo(MissionStatus.COMPLETED);
            assertThat(mission.getCompletedAt()).isNotNull();
            assertThat(device.getStatus()).isEqualTo(DeviceStatus.AVAILABLE);
        }

        @Test
        @DisplayName("4. completeMission throws exception when status is SCHEDULED (fully invalid for completion)")
        void completeMission_invalidStatus_throws() {
            Mission mission = buildMission("m-8", MissionStatus.SCHEDULED);
            when(missionRepository.findById("m-8")).thenReturn(Optional.of(mission));

            assertThatThrownBy(() -> missionService.completeMission("m-8"))
                    .isInstanceOf(ApiException.class)
                    .hasMessageContaining("PENDING_REVIEW");
        }

        @Test
        void completeMission_rejectsMissingPostflightInspection() {
            Mission mission = buildMission("m-8", MissionStatus.PENDING_REVIEW);
            when(missionRepository.findById("m-8")).thenReturn(Optional.of(mission));
            when(postDeviceCheckRepository.findFirstByMissionIdOrderByCreatedAtDesc("m-8"))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> missionService.completeMission("m-8"))
                    .isInstanceOf(ApiException.class)
                    .hasMessageContaining("post-flight inspection");
        }

        @Test
        @DisplayName("5. failMission keeps an airborne device unavailable after failure")
        void failMission_setsDeviceAvailable() {
            Mission mission = buildMission("m-9", MissionStatus.IN_FLIGHT);
            Device device = buildDevice("DEV-01", DeviceStatus.ACTIVE_MISSION);
            MissionDeviceAssignment mda = new MissionDeviceAssignment();
            mda.setDevice(device);
            when(missionDeviceAssignmentRepository.findFirstByMissionIdOrderByCreatedAtDesc("m-9"))
                    .thenReturn(Optional.of(mda));

            when(missionRepository.findById("m-9")).thenReturn(Optional.of(mission));
            when(missionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(deviceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            MissionResponse result = missionService.failMission("m-9", "GPS lost");

            assertThat(result.getStatus()).isEqualTo(MissionStatus.FAILED);
            assertThat(device.getStatus()).isEqualTo(DeviceStatus.MAINTENANCE);
        }

        @Test
        @DisplayName("6. updatePostFlightStatus success updates device status and completes mission")
        void updatePostFlightStatus_success() {
            Mission mission = buildMission("m-post", MissionStatus.POSTFLIGHT_CHECKING);
            Device device = buildDevice("DEV-01", DeviceStatus.RETURNING);
            MissionDeviceAssignment mda = new MissionDeviceAssignment();
            mda.setDevice(device);
            when(missionDeviceAssignmentRepository.findFirstByMissionIdOrderByCreatedAtDesc("m-post"))
                    .thenReturn(Optional.of(mda));

            when(missionRepository.findById("m-post")).thenReturn(Optional.of(mission));
            when(missionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(deviceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            MissionResponse result = missionService.updatePostFlightStatus("m-post", DeviceStatus.AVAILABLE,
                    "Cánh quạt bình thường");

            assertThat(device.getStatus()).isEqualTo(DeviceStatus.AVAILABLE);
            assertThat(result.getStatus()).isEqualTo(MissionStatus.PENDING_REVIEW);
        }

        @Test
        @DisplayName("7. updatePostFlightStatus throws exception if mission has no assigned device")
        void updatePostFlightStatus_noDeviceAssigned_throws() {
            Mission mission = buildMission("m-nodev", MissionStatus.POSTFLIGHT_CHECKING);
            when(missionDeviceAssignmentRepository.findFirstByMissionIdOrderByCreatedAtDesc("m-nodev"))
                    .thenReturn(Optional.empty());

            when(missionRepository.findById("m-nodev")).thenReturn(Optional.of(mission));

            assertThatThrownBy(() -> missionService.updatePostFlightStatus("m-nodev", DeviceStatus.AVAILABLE, "Notes"))
                    .isInstanceOf(ApiException.class)
                    .hasMessageContaining("has no assigned");
        }
    }

    // =========================================================================
    // Acceptance Criteria Tests
    // =========================================================================
    @Nested
    @DisplayName("AC – Resource Assignment & Safety")
    class AC_ResourceAssignmentAndSafety {

        @Test
        @DisplayName("AC-1. assignDevice throws when device is under MAINTENANCE")
        void assignDevice_maintenanceDevice_blocked() {
            Mission mission = buildMission("m-assign", MissionStatus.RESOURCE_ASSIGNING);
            mission.setScheduledStartAt(Instant.now().plusSeconds(3600));
            mission.setScheduledEndAt(Instant.now().plusSeconds(7200));
            Device maintenanceDevice = buildDevice("DEV-BROKEN", DeviceStatus.MAINTENANCE);

            when(missionRepository.findById("m-assign")).thenReturn(Optional.of(mission));
            when(deviceRepository.findById("DEV-BROKEN-id")).thenReturn(Optional.of(maintenanceDevice));

            AssignDeviceRequest request = new AssignDeviceRequest();
            request.setDeviceId("DEV-BROKEN-id");

            assertThatThrownBy(() -> missionService.assignDevice("m-assign", request))
                    .isInstanceOf(ApiException.class)
                    .hasMessageContaining("MAINTENANCE");
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private Mission buildMission(String id, MissionStatus status) {
        Mission m = new Mission();
        m.setId(id);
        m.setMissionCode("MC-" + id);
        m.setStatus(status);
        return m;
    }

    private MissionStaffAssignment staffAssignment(Mission mission, String staffId, String status) {
        return staffAssignment(mission, staffId, MissionStaffRole.PILOT, status);
    }

    private MissionStaffAssignment staffAssignment(
            Mission mission,
            String staffId,
            MissionStaffRole role,
            String status) {
        MissionStaffAssignment assignment = new MissionStaffAssignment();
        assignment.setMission(mission);
        User staff = new User();
        staff.setId(staffId);
        assignment.setStaff(staff);
        assignment.setAssignedRole(role);
        assignment.setResponseStatus(StaffResponseStatus.valueOf(status));
        assignment.setIsCurrent(true);
        return assignment;
    }

    private Device buildDevice(String code, DeviceStatus status) {
        Device d = new Device();
        d.setId(code + "-id");
        d.setDeviceCode(code);
        d.setStatus(status);
        return d;
    }
}
