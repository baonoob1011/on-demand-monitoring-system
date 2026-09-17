package com.ondemandmonitoring.mission.service;

import com.ondemandmonitoring.common.exception.ApiException;
import com.ondemandmonitoring.drone.domain.Drone;
import com.ondemandmonitoring.drone.domain.PreflightCheck;
import com.ondemandmonitoring.drone.dto.response.PreflightCheckResponse;
import com.ondemandmonitoring.drone.enums.DroneStatus;
import com.ondemandmonitoring.drone.repository.DroneRepository;
import com.ondemandmonitoring.drone.service.PreflightCheckService;
import com.ondemandmonitoring.mission.domain.FlightToken;
import com.ondemandmonitoring.mission.domain.Mission;
import com.ondemandmonitoring.mission.dto.response.FlightTokenResponse;
import com.ondemandmonitoring.mission.dto.response.MissionResponse;
import com.ondemandmonitoring.mission.enums.MissionStatus;
import com.ondemandmonitoring.mission.mapper.FlightTokenMapper;
import com.ondemandmonitoring.mission.mapper.MissionMapper;
import com.ondemandmonitoring.drone.mapper.PreflightCheckMapper;
import com.ondemandmonitoring.mission.repository.FlightTokenRepository;
import com.ondemandmonitoring.mission.repository.MissionRepository;
import com.ondemandmonitoring.mission.service.impl.MissionService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.ondemandmonitoring.drone.repository.MaintenanceTicketRepository;
import com.ondemandmonitoring.mission.repository.*;

class MissionServiceTest {

    MissionRepository missionRepository;
    DroneRepository droneRepository;
    FlightTokenRepository flightTokenRepository;
    PreflightCheckService preflightCheckService;
    MissionMapper missionMapper;
    FlightTokenMapper flightTokenMapper;
    PreflightCheckMapper preflightCheckMapper;

    MissionDroneAssignmentRepository missionDroneAssignmentRepository;
    MissionOperatorAssignmentRepository missionOperatorAssignmentRepository;
    GcsSessionRepository gcsSessionRepository;
    ControlHandoverRepository controlHandoverRepository;
    PostflightCheckRepository postflightCheckRepository;
    private MaintenanceTicketRepository maintenanceTicketRepository;
    private com.ondemandmonitoring.order.repository.OrderRepository orderRepository;
    private com.ondemandmonitoring.user.repository.UserRepository userRepository;

    MissionService missionService;

    @BeforeEach
    void setUp() {
        missionRepository                     = mock(MissionRepository.class);
        droneRepository                      = mock(DroneRepository.class);
        flightTokenRepository                 = mock(FlightTokenRepository.class);
        preflightCheckService                 = mock(PreflightCheckService.class);
        missionMapper                         = mock(MissionMapper.class);
        flightTokenMapper                     = mock(FlightTokenMapper.class);
        preflightCheckMapper                  = mock(PreflightCheckMapper.class);
        missionDroneAssignmentRepository     = mock(MissionDroneAssignmentRepository.class);
        missionOperatorAssignmentRepository  = mock(MissionOperatorAssignmentRepository.class);
        gcsSessionRepository                  = mock(GcsSessionRepository.class);
        controlHandoverRepository             = mock(ControlHandoverRepository.class);
        postflightCheckRepository             = mock(PostflightCheckRepository.class);
        maintenanceTicketRepository          = mock(MaintenanceTicketRepository.class);
        orderRepository                      = mock(com.ondemandmonitoring.order.repository.OrderRepository.class);
        userRepository                       = mock(com.ondemandmonitoring.user.repository.UserRepository.class);

        missionService = new MissionService(
                missionRepository,
                droneRepository,
                flightTokenRepository,
                preflightCheckService,
                missionMapper,
                flightTokenMapper,
                preflightCheckMapper,
                missionDroneAssignmentRepository,
                missionOperatorAssignmentRepository,
                gcsSessionRepository,
                controlHandoverRepository,
                postflightCheckRepository,
                maintenanceTicketRepository,
                orderRepository,
                userRepository
        );

        when(missionMapper.toResponse(any())).thenAnswer(inv -> {
            Mission m = inv.getArgument(0);
            if (m == null) return null;
            return MissionResponse.builder()
                    .id(m.getId())
                    .missionCode(m.getMissionCode())
                    .status(m.getStatus())
                    // operatorId, droneId, droneCode are no longer directly on Mission.
                    // The test should assert repository interactions instead.
                    .rejectionReason(m.getRejectionReason())
                    .failureReason(m.getFailureReason())
                    .startedAt(m.getStartedAt())
                    .completedAt(m.getCompletedAt())
                    .build();
        });

        when(flightTokenMapper.toResponse(any())).thenAnswer(inv -> {
            FlightToken ft = inv.getArgument(0);
            if (ft == null) return null;
            return FlightTokenResponse.builder()
                    .tokenValue(ft.getTokenValue())
                    .build();
        });

        when(preflightCheckMapper.toResponse(any(), any())).thenAnswer(inv -> {
            PreflightCheck pc = inv.getArgument(0);
            FlightTokenResponse ft = inv.getArgument(1);
            if (pc == null) return null;
            return PreflightCheckResponse.builder()
                    .overallPassed(pc.getOverallPassed())
                    .flightToken(ft)
                    .build();
        });
    }

    // =========================================================================
    // F3.1 – Operator Acceptance / Rejection (6 Test Cases)
    // =========================================================================
    @Nested
    @DisplayName("F3.1 - Operator Acceptance & Rejection")
    class F3_1_OperatorAcceptanceAndRejection {

        @Test
        @DisplayName("1. acceptMission success when WAITING_OPERATOR_ACCEPTANCE")
        void acceptMission_success() {
            Mission mission = buildMission("m-1", MissionStatus.WAITING_OPERATOR_ACCEPTANCE);
            when(missionRepository.findById("m-1")).thenReturn(Optional.of(mission));
            when(missionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            MissionResponse result = missionService.acceptMission("m-1", "00000000-0000-0000-0000-000000000001");

            assertThat(result.getStatus()).isEqualTo(MissionStatus.SCHEDULED);
            verify(missionOperatorAssignmentRepository, atLeastOnce()).save(any());
        }

        @Test
        @DisplayName("2. acceptMission throws exception when mission not found")
        void acceptMission_notFound_throws() {
            when(missionRepository.findById("m-missing")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> missionService.acceptMission("m-missing", "00000000-0000-0000-0000-000000000001"))
                    .isInstanceOf(ApiException.class)
                    .hasMessageContaining("Mission not found");
        }

        @Test
        @DisplayName("3. acceptMission throws exception when status is invalid")
        void acceptMission_wrongStatus_throws() {
            Mission mission = buildMission("m-1", MissionStatus.SCHEDULED);
            when(missionRepository.findById("m-1")).thenReturn(Optional.of(mission));

            assertThatThrownBy(() -> missionService.acceptMission("m-1", "00000000-0000-0000-0000-000000000001"))
                    .isInstanceOf(ApiException.class)
                    .hasMessageContaining("must be WAITING_OPERATOR_ACCEPTANCE");
        }

        @Test
        @DisplayName("4. rejectMission success when WAITING_OPERATOR_ACCEPTANCE")
        void rejectMission_success() {
            Mission mission = buildMission("m-2", MissionStatus.WAITING_OPERATOR_ACCEPTANCE);
            when(missionRepository.findById("m-2")).thenReturn(Optional.of(mission));
            when(missionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            MissionResponse result = missionService.rejectMission("m-2", "00000000-0000-0000-0000-000000000001", "Trùng lịch cá nhân");

            assertThat(result.getStatus()).isEqualTo(MissionStatus.RESOURCE_ASSIGNING);
            assertThat(result.getRejectionReason()).isEqualTo("Trùng lịch cá nhân");
            verify(missionOperatorAssignmentRepository, atLeastOnce()).save(any());
        }

        @Test
        @DisplayName("5. rejectMission throws exception when mission not found")
        void rejectMission_notFound_throws() {
            when(missionRepository.findById("m-missing")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> missionService.rejectMission("m-missing", "00000000-0000-0000-0000-000000000001", "Bận"))
                    .isInstanceOf(ApiException.class)
                    .hasMessageContaining("Mission not found");
        }

        @Test
        @DisplayName("6. rejectMission throws exception when status is invalid")
        void rejectMission_wrongStatus_throws() {
            Mission mission = buildMission("m-2", MissionStatus.IN_FLIGHT);
            when(missionRepository.findById("m-2")).thenReturn(Optional.of(mission));

            assertThatThrownBy(() -> missionService.rejectMission("m-2", "00000000-0000-0000-0000-000000000001", "Trùng lịch"))
                    .isInstanceOf(ApiException.class)
                    .hasMessageContaining("must be WAITING_OPERATOR_ACCEPTANCE");
        }
    }

    // =========================================================================
    // F3.2 – GCS Connect & Digital Pre-flight Check (6 Test Cases)
    // =========================================================================
    @Nested
    @DisplayName("F3.2 - GCS Connect & Pre-flight Gate")
    class F3_2_GcsConnectAndPreflightGate {

        @Test
        @DisplayName("1. connectGcs success from SCHEDULED status")
        void connectGcs_success_fromScheduled() {
            Mission mission = buildMission("m-gcs", MissionStatus.SCHEDULED);
            Drone drone = buildDrone("DRONE-01", DroneStatus.AVAILABLE);
            com.ondemandmonitoring.mission.domain.MissionDroneAssignment mda = new com.ondemandmonitoring.mission.domain.MissionDroneAssignment();
            mda.setDrone(drone);
            when(missionDroneAssignmentRepository.findByMissionIdAndIsCurrentTrue(mission.getId())).thenReturn(Optional.of(mda));

            when(missionRepository.findById("m-gcs")).thenReturn(Optional.of(mission));
            when(missionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(droneRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            MissionResponse result = missionService.connectGcs("m-gcs");

            assertThat(result.getStatus()).isEqualTo(MissionStatus.CONNECTED);
            assertThat(drone.getStatus()).isEqualTo(DroneStatus.PREFLIGHT);
        }

        @Test
        @DisplayName("2. connectGcs success from CONNECTED status")
        void connectGcs_success_fromConnected() {
            Mission mission = buildMission("m-gcs2", MissionStatus.CONNECTED);
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
        @DisplayName("4. runPreflightCheck passed issues FlightToken and updates status to READY_TO_FLY")
        void runPreflightCheck_passed_issuesFlightToken_missionBecomesReadyToFly() {
            Mission mission = buildMission("m-3", MissionStatus.CONNECTED);
            Drone drone = buildDrone("DRONE-01", DroneStatus.AVAILABLE);
            PreflightCheck passedCheck = new PreflightCheck();
            passedCheck.setOverallPassed(true);
            passedCheck.setDrone(drone);
            passedCheck.setMissionId("m-3");

            when(missionRepository.findById("m-3")).thenReturn(Optional.of(mission));
            when(droneRepository.findByDroneCode("DRONE-01")).thenReturn(Optional.of(drone));
            when(droneRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(missionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(preflightCheckService.run("DRONE-01", "m-3")).thenReturn(passedCheck);
            when(flightTokenRepository.save(any())).thenAnswer(inv -> {
                FlightToken t = inv.getArgument(0);
                t.setId("ft-1");
                return t;
            });

            PreflightCheckResponse result = missionService.runPreflightCheck("m-3", "DRONE-01");

            assertThat(result.getOverallPassed()).isTrue();
            assertThat(result.getFlightToken()).isNotNull();
            assertThat(mission.getStatus()).isEqualTo(MissionStatus.READY_TO_FLY);
        }

        @Test
        @DisplayName("5. runPreflightCheck hardware failed routes drone to MAINTENANCE and mission to PENDING_APPROVAL")
        void runPreflightCheck_hardwareFailed_routesToMaintenance_andPendingApproval() {
            Mission mission = buildMission("m-fail", MissionStatus.CONNECTED);
            Drone drone = buildDrone("DRONE-01", DroneStatus.PREFLIGHT);
            PreflightCheck failedCheck = new PreflightCheck();
            failedCheck.setOverallPassed(false);
            failedCheck.setFailureReason("Gyrometer failure");
            failedCheck.setFaultType("HARDWARE");
            failedCheck.setDrone(drone);

            when(missionRepository.findById("m-fail")).thenReturn(Optional.of(mission));
            when(droneRepository.findByDroneCode("DRONE-01")).thenReturn(Optional.of(drone));
            when(droneRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(missionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(preflightCheckService.run("DRONE-01", "m-fail")).thenReturn(failedCheck);

            PreflightCheckResponse result = missionService.runPreflightCheck("m-fail", "DRONE-01");

            assertThat(result.getOverallPassed()).isFalse();
            assertThat(drone.getStatus()).isEqualTo(DroneStatus.MAINTENANCE);
            assertThat(mission.getStatus()).isEqualTo(MissionStatus.PENDING_APPROVAL);
        }

        @Test
        @DisplayName("6. runPreflightCheck battery low routes drone to IDLE_CHARGING and mission to PENDING_APPROVAL")
        void runPreflightCheck_batteryLow_routesToIdleCharging_andPendingApproval() {
            Mission mission = buildMission("m-bat", MissionStatus.CONNECTED);
            Drone drone = buildDrone("DRONE-01", DroneStatus.PREFLIGHT);
            PreflightCheck batteryLowCheck = new PreflightCheck();
            batteryLowCheck.setOverallPassed(false);
            batteryLowCheck.setFailureReason("Battery is below 80%");
            batteryLowCheck.setFaultType("BATTERY");
            batteryLowCheck.setBatteryPercent(45.0);
            batteryLowCheck.setDrone(drone);

            when(missionRepository.findById("m-bat")).thenReturn(Optional.of(mission));
            when(droneRepository.findByDroneCode("DRONE-01")).thenReturn(Optional.of(drone));
            when(droneRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(missionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(preflightCheckService.run("DRONE-01", "m-bat")).thenReturn(batteryLowCheck);

            PreflightCheckResponse result = missionService.runPreflightCheck("m-bat", "DRONE-01");

            assertThat(result.getOverallPassed()).isFalse();
            assertThat(drone.getStatus()).isEqualTo(DroneStatus.IDLE_CHARGING);
            assertThat(mission.getStatus()).isEqualTo(MissionStatus.PENDING_APPROVAL);
        }
    }

    // =========================================================================
    // F3.2 – Drone Replacement & Control Handover (6 Test Cases)
    // =========================================================================
    @Nested
    @DisplayName("F3.2 - Drone Replacement & Control Handover")
    class F3_2_DroneReplacementAndHandover {

        @Test
        @DisplayName("1. replaceDrone success resets mission status to CONNECTED and marks old drone MAINTENANCE")
        void replaceDrone_success() {
            Mission mission = buildMission("m-6", MissionStatus.FAILED_PREFLIGHT);
            Drone brokenDrone = buildDrone("DRONE-BAD", DroneStatus.PREFLIGHT);
            com.ondemandmonitoring.mission.domain.MissionDroneAssignment mda = new com.ondemandmonitoring.mission.domain.MissionDroneAssignment();
            mda.setDrone(brokenDrone);
            when(missionDroneAssignmentRepository.findByMissionIdAndIsCurrentTrue(mission.getId())).thenReturn(Optional.of(mda));

            Drone goodDrone = buildDrone("DRONE-OK", DroneStatus.AVAILABLE);
            goodDrone.setId("dev-ok");

            when(missionRepository.findById("m-6")).thenReturn(Optional.of(mission));
            when(droneRepository.findByDroneCode("DRONE-OK")).thenReturn(Optional.of(goodDrone));
            when(missionRepository.findActiveByDroneId("dev-ok")).thenReturn(List.of());
            when(droneRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(missionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            MissionResponse result = missionService.replaceDrone("m-6", "DRONE-OK");

            verify(missionDroneAssignmentRepository, atLeastOnce()).save(any());
            assertThat(brokenDrone.getStatus()).isEqualTo(DroneStatus.MAINTENANCE);
            assertThat(result.getStatus()).isEqualTo(MissionStatus.CONNECTED);
        }

        @Test
        @DisplayName("2. replaceDrone throws when new drone code not found")
        void replaceDrone_notFound_throws() {
            Mission mission = buildMission("m-4", MissionStatus.FAILED_PREFLIGHT);
            when(missionRepository.findById("m-4")).thenReturn(Optional.of(mission));
            when(droneRepository.findByDroneCode("DRONE-GHOST")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> missionService.replaceDrone("m-4", "DRONE-GHOST"))
                    .isInstanceOf(ApiException.class)
                    .hasMessageContaining("không tồn tại");
        }

        @Test
        @DisplayName("3. replaceDrone throws when new drone is not AVAILABLE")
        void replaceDrone_newDroneUnavailable_throws() {
            Mission mission = buildMission("m-4", MissionStatus.FAILED_PREFLIGHT);
            Drone busyDrone = buildDrone("DRONE-02", DroneStatus.ACTIVE_MISSION);

            when(missionRepository.findById("m-4")).thenReturn(Optional.of(mission));
            when(droneRepository.findByDroneCode("DRONE-02")).thenReturn(Optional.of(busyDrone));

            assertThatThrownBy(() -> missionService.replaceDrone("m-4", "DRONE-02"))
                    .isInstanceOf(ApiException.class)
                    .hasMessageContaining("is not AVAILABLE");
        }

        @Test
        @DisplayName("4. replaceDrone throws when new drone has active schedule conflict")
        void replaceDrone_scheduleConflict_throws() {
            Mission mission = buildMission("m-4", MissionStatus.FAILED_PREFLIGHT);
            Drone drone = buildDrone("DRONE-02", DroneStatus.AVAILABLE);
            drone.setId("dev-2");

            Mission conflictingMission = buildMission("m-other", MissionStatus.SCHEDULED);

            when(missionRepository.findById("m-4")).thenReturn(Optional.of(mission));
            when(droneRepository.findByDroneCode("DRONE-02")).thenReturn(Optional.of(drone));
            when(missionRepository.findActiveByDroneId("dev-2")).thenReturn(List.of(conflictingMission));

            assertThatThrownBy(() -> missionService.replaceDrone("m-4", "DRONE-02"))
                    .isInstanceOf(ApiException.class)
                    .hasMessageContaining("đang được lên lịch");
        }

        @Test
        @DisplayName("5. handoverControl success when READY_TO_FLY")
        void handoverControl_success() {
            Mission mission = buildMission("m-5", MissionStatus.READY_TO_FLY);
            when(missionRepository.findById("m-5")).thenReturn(Optional.of(mission));
            when(missionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            MissionResponse result = missionService.handoverControl("m-5", "00000000-0000-0000-0000-000000000002");

            verify(controlHandoverRepository, atLeastOnce()).save(any());
        }

        @Test
        @DisplayName("6. handoverControl throws when status is not READY_TO_FLY")
        void handoverControl_invalidStatus_throws() {
            Mission mission = buildMission("m-5", MissionStatus.SCHEDULED);
            when(missionRepository.findById("m-5")).thenReturn(Optional.of(mission));

            assertThatThrownBy(() -> missionService.handoverControl("m-5", "00000000-0000-0000-0000-000000000002"))
                    .isInstanceOf(ApiException.class)
                    .hasMessageContaining("must be READY_TO_FLY");
        }
    }

    // =========================================================================
    // F3.3 – Takeoff & Execution Lifecycle (6 Test Cases)
    // =========================================================================
    @Nested
    @DisplayName("F3.3 - Takeoff & Flight Lifecycle")
    class F3_3_TakeoffAndFlightLifecycle {

        @Test
        @DisplayName("1. startMission success with valid FlightToken")
        void startMission_success_withValidToken() {
            Mission mission = buildMission("m-7", MissionStatus.READY_TO_FLY);
            Drone drone = buildDrone("DRONE-01", DroneStatus.PREFLIGHT);
            com.ondemandmonitoring.mission.domain.MissionDroneAssignment mda = new com.ondemandmonitoring.mission.domain.MissionDroneAssignment();
            mda.setDrone(drone);
            when(missionDroneAssignmentRepository.findByMissionIdAndIsCurrentTrue(mission.getId())).thenReturn(Optional.of(mda));

            FlightToken token = new FlightToken();
            token.setTokenValue("valid-token");
            token.setExpiresAt(Instant.now().plusSeconds(600));
            token.setUsed(false);
            token.setRevoked(false);

            when(missionRepository.findById("m-7")).thenReturn(Optional.of(mission));
            when(flightTokenRepository.findByTokenValue("valid-token")).thenReturn(Optional.of(token));
            when(missionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(droneRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            MissionResponse result = missionService.startMission("m-7", "valid-token");

            assertThat(result.getStatus()).isEqualTo(MissionStatus.IN_FLIGHT);
            assertThat(result.getStartedAt()).isNotNull();
            assertThat(drone.getStatus()).isEqualTo(DroneStatus.ACTIVE_MISSION);
            assertThat(token.isUsed()).isTrue();
        }

        @Test
        @DisplayName("2. startMission throws and revokes token when FlightToken is expired")
        void startMission_expiredToken_revokesToken_throws() {
            Mission mission = buildMission("m-7", MissionStatus.READY_TO_FLY);

            FlightToken expiredToken = new FlightToken();
            expiredToken.setTokenValue("expired-token");
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
            Drone drone = buildDrone("DRONE-01", DroneStatus.PREFLIGHT);
            com.ondemandmonitoring.mission.domain.MissionDroneAssignment mda = new com.ondemandmonitoring.mission.domain.MissionDroneAssignment();
            mda.setDrone(drone);
            when(missionDroneAssignmentRepository.findByMissionIdAndIsCurrentTrue(mission.getId())).thenReturn(Optional.of(mda));

            FlightToken autoToken = new FlightToken();
            autoToken.setMissionId("m-7");
            autoToken.setExpiresAt(Instant.now().plusSeconds(300));

            when(missionRepository.findById("m-7")).thenReturn(Optional.of(mission));
            when(flightTokenRepository.findByMissionIdAndUsedFalseAndRevokedFalse("m-7")).thenReturn(Optional.of(autoToken));
            when(missionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(droneRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            MissionResponse result = missionService.startMission("m-7");

            assertThat(result.getStatus()).isEqualTo(MissionStatus.IN_FLIGHT);
            assertThat(autoToken.isUsed()).isTrue();
        }

        @Test
        @DisplayName("5. startMission throws exception when status is not READY_TO_FLY")
        void startMission_invalidStatus_throws() {
            Mission mission = buildMission("m-7", MissionStatus.SCHEDULED);
            when(missionRepository.findById("m-7")).thenReturn(Optional.of(mission));

            assertThatThrownBy(() -> missionService.startMission("m-7"))
                    .isInstanceOf(ApiException.class)
                    .hasMessageContaining("must be READY_TO_FLY");
        }

        @Test
        @DisplayName("6. markReturning success from IN_FLIGHT or IN_PROGRESS")
        void markReturning_success() {
            Mission mission = buildMission("m-ret", MissionStatus.IN_FLIGHT);
            Drone drone = buildDrone("DRONE-01", DroneStatus.ACTIVE_MISSION);
            com.ondemandmonitoring.mission.domain.MissionDroneAssignment mda = new com.ondemandmonitoring.mission.domain.MissionDroneAssignment();
            mda.setDrone(drone);
            when(missionDroneAssignmentRepository.findByMissionIdAndIsCurrentTrue(mission.getId())).thenReturn(Optional.of(mda));

            when(missionRepository.findById("m-ret")).thenReturn(Optional.of(mission));
            when(missionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(droneRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            MissionResponse result = missionService.markReturning("m-ret");

            assertThat(result.getStatus()).isEqualTo(MissionStatus.RETURNING);
            assertThat(drone.getStatus()).isEqualTo(DroneStatus.RETURNING);
        }
    }

    // =========================================================================
    // F3.4 & F3.5 – Postflight, Complete, Fail & Drone Status (7 Test Cases)
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

            MissionResponse result = missionService.startPostflightChecking("m-pf");

            assertThat(result.getStatus()).isEqualTo(MissionStatus.POSTFLIGHT_CHECKING);
        }

        @Test
        @DisplayName("2. startPostflightChecking throws exception when status is not RETURNING")
        void startPostflightChecking_invalidStatus_throws() {
            Mission mission = buildMission("m-pf", MissionStatus.IN_FLIGHT);
            when(missionRepository.findById("m-pf")).thenReturn(Optional.of(mission));

            assertThatThrownBy(() -> missionService.startPostflightChecking("m-pf"))
                    .isInstanceOf(ApiException.class)
                    .hasMessageContaining("must be RETURNING");
        }

        @Test
        @DisplayName("3. completeMission success when POSTFLIGHT_CHECKING")
        void completeMission_success() {
            Mission mission = buildMission("m-8", MissionStatus.POSTFLIGHT_CHECKING);
            Drone drone = buildDrone("DRONE-01", DroneStatus.RETURNING);
            com.ondemandmonitoring.mission.domain.MissionDroneAssignment mda = new com.ondemandmonitoring.mission.domain.MissionDroneAssignment();
            mda.setDrone(drone);
            when(missionDroneAssignmentRepository.findByMissionIdAndIsCurrentTrue(mission.getId())).thenReturn(Optional.of(mda));

            when(missionRepository.findById("m-8")).thenReturn(Optional.of(mission));
            when(missionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(droneRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            MissionResponse result = missionService.completeMission("m-8");

            assertThat(result.getStatus()).isEqualTo(MissionStatus.COMPLETED);
            assertThat(result.getCompletedAt()).isNotNull();
            assertThat(drone.getStatus()).isEqualTo(DroneStatus.AVAILABLE);
        }

        @Test
        @DisplayName("4. completeMission throws exception when status is not POSTFLIGHT_CHECKING")
        void completeMission_invalidStatus_throws() {
            Mission mission = buildMission("m-8", MissionStatus.IN_FLIGHT);
            when(missionRepository.findById("m-8")).thenReturn(Optional.of(mission));

            assertThatThrownBy(() -> missionService.completeMission("m-8"))
                    .isInstanceOf(ApiException.class)
                    .hasMessageContaining("must be POSTFLIGHT_CHECKING");
        }

        @Test
        @DisplayName("5. failMission sets mission FAILED and drone status AVAILABLE")
        void failMission_setsDeviceAvailable() {
            Mission mission = buildMission("m-9", MissionStatus.IN_FLIGHT);
            Drone drone = buildDrone("DRONE-01", DroneStatus.ACTIVE_MISSION);
            com.ondemandmonitoring.mission.domain.MissionDroneAssignment mda = new com.ondemandmonitoring.mission.domain.MissionDroneAssignment();
            mda.setDrone(drone);
            when(missionDroneAssignmentRepository.findByMissionIdAndIsCurrentTrue(mission.getId())).thenReturn(Optional.of(mda));

            when(missionRepository.findById("m-9")).thenReturn(Optional.of(mission));
            when(missionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(droneRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            MissionResponse result = missionService.failMission("m-9", "GPS lost");

            assertThat(result.getStatus()).isEqualTo(MissionStatus.FAILED);
            assertThat(result.getFailureReason()).isEqualTo("GPS lost");
            assertThat(drone.getStatus()).isEqualTo(DroneStatus.AVAILABLE);
        }

        @Test
        @DisplayName("6. updatePostFlightStatus success updates drone status and completes mission")
        void updatePostFlightStatus_success() {
            Mission mission = buildMission("m-post", MissionStatus.POSTFLIGHT_CHECKING);
            Drone drone = buildDrone("DRONE-01", DroneStatus.RETURNING);
            com.ondemandmonitoring.mission.domain.MissionDroneAssignment mda = new com.ondemandmonitoring.mission.domain.MissionDroneAssignment();
            mda.setDrone(drone);
            when(missionDroneAssignmentRepository.findByMissionIdAndIsCurrentTrue(mission.getId())).thenReturn(Optional.of(mda));

            when(missionRepository.findById("m-post")).thenReturn(Optional.of(mission));
            when(missionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(droneRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            MissionResponse result = missionService.updatePostFlightStatus("m-post", DroneStatus.AVAILABLE, "Cánh quạt bình thường");

            assertThat(drone.getStatus()).isEqualTo(DroneStatus.AVAILABLE);
            assertThat(result.getStatus()).isEqualTo(MissionStatus.COMPLETED);
            assertThat(result.getCompletedAt()).isNotNull();
        }

        @Test
        @DisplayName("7. updatePostFlightStatus throws exception if mission has no assigned drone")
        void updatePostFlightStatus_noDeviceAssigned_throws() {
            Mission mission = buildMission("m-nodev", MissionStatus.POSTFLIGHT_CHECKING);
            when(missionDroneAssignmentRepository.findByMissionIdAndIsCurrentTrue(mission.getId())).thenReturn(Optional.empty());

            when(missionRepository.findById("m-nodev")).thenReturn(Optional.of(mission));

            assertThatThrownBy(() -> missionService.updatePostFlightStatus("m-nodev", DroneStatus.AVAILABLE, "Notes"))
                    .isInstanceOf(ApiException.class)
                    .hasMessageContaining("has no assigned drone");
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

    private Drone buildDrone(String code, DroneStatus status) {
        Drone d = new Drone();
        d.setId(code + "-id");
        d.setDroneCode(code);
        d.setStatus(status);
        return d;
    }
}
