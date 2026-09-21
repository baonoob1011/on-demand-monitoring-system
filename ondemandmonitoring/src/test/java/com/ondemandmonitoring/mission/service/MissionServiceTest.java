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
import com.ondemandmonitoring.mission.domain.MissionOperatorAssignment;
import com.ondemandmonitoring.mission.domain.MissionPlan;
import com.ondemandmonitoring.mission.dto.response.FlightTokenResponse;
import com.ondemandmonitoring.mission.dto.response.MissionResponse;
import com.ondemandmonitoring.mission.enums.FeasibilityStatus;
import com.ondemandmonitoring.mission.enums.MissionStatus;
import com.ondemandmonitoring.mission.enums.PlanningAlgorithm;
import com.ondemandmonitoring.mission.mapper.FlightTokenMapper;
import com.ondemandmonitoring.mission.mapper.MissionMapper;
import com.ondemandmonitoring.drone.mapper.PreflightCheckMapper;
import com.ondemandmonitoring.mission.repository.FlightTokenRepository;
import com.ondemandmonitoring.mission.repository.MissionRepository;
import com.ondemandmonitoring.mission.service.impl.MissionService;
import com.ondemandmonitoring.planning.service.MissionPlanningService;

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
    MissionPlanRepository missionPlanRepository;
    MissionPlanningService missionPlanningService;
    GcsSessionRepository gcsSessionRepository;
    ControlHandoverRepository controlHandoverRepository;
    PostflightCheckRepository postflightCheckRepository;
    MaintenanceTicketRepository maintenanceTicketRepository;
    com.ondemandmonitoring.order.repository.OrderRepository orderRepository;

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
        missionPlanRepository                = mock(MissionPlanRepository.class);
        missionPlanningService               = mock(MissionPlanningService.class);
        gcsSessionRepository                  = mock(GcsSessionRepository.class);
        controlHandoverRepository             = mock(ControlHandoverRepository.class);
        postflightCheckRepository             = mock(PostflightCheckRepository.class);
        maintenanceTicketRepository          = mock(MaintenanceTicketRepository.class);
        orderRepository                      = mock(com.ondemandmonitoring.order.repository.OrderRepository.class);

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
                missionPlanRepository,
                missionPlanningService,
                gcsSessionRepository,
                controlHandoverRepository,
                postflightCheckRepository,
                maintenanceTicketRepository,
                orderRepository
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

        when(missionPlanRepository.findByMissionId(any())).thenAnswer(inv -> Optional.of(feasiblePlan(inv.getArgument(0))));
        when(missionPlanningService.generateAStarEnergyAwarePlan(any())).thenAnswer(inv -> feasiblePlan(inv.getArgument(0)));
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
            when(missionOperatorAssignmentRepository.findByMissionIdAndIsCurrentTrue("m-1"))
                    .thenReturn(Optional.of(operatorAssignment(mission, "op-01", "PENDING")));
            when(missionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            MissionResponse result = missionService.acceptMission("m-1", "op-01");

            assertThat(result.getStatus()).isEqualTo(MissionStatus.SCHEDULED);
            verify(missionPlanningService).generateAStarEnergyAwarePlan("m-1");
            verify(missionOperatorAssignmentRepository, atLeastOnce()).save(any());
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
                    .hasMessageContaining("must be WAITING_OPERATOR_ACCEPTANCE");
            verifyNoInteractions(missionPlanningService);
        }

        @Test
        @DisplayName("4. acceptMission rejects wrong operator before planning")
        void acceptMission_wrongOperator_doesNotPlan() {
            Mission mission = buildMission("m-operator", MissionStatus.WAITING_OPERATOR_ACCEPTANCE);
            when(missionRepository.findById("m-operator")).thenReturn(Optional.of(mission));
            when(missionOperatorAssignmentRepository.findByMissionIdAndIsCurrentTrue("m-operator"))
                    .thenReturn(Optional.of(operatorAssignment(mission, "op-expected", "PENDING")));

            assertThatThrownBy(() -> missionService.acceptMission("m-operator", "op-other"))
                    .isInstanceOf(ApiException.class)
                    .hasMessageContaining("assigned to another operator");
            verifyNoInteractions(missionPlanningService);
        }

        @Test
        @DisplayName("5. acceptMission rejects non-pending assignment before planning")
        void acceptMission_rejectedAssignment_doesNotPlan() {
            Mission mission = buildMission("m-rejected", MissionStatus.WAITING_OPERATOR_ACCEPTANCE);
            when(missionRepository.findById("m-rejected")).thenReturn(Optional.of(mission));
            when(missionOperatorAssignmentRepository.findByMissionIdAndIsCurrentTrue("m-rejected"))
                    .thenReturn(Optional.of(operatorAssignment(mission, "op-01", "REJECTED")));

            assertThatThrownBy(() -> missionService.acceptMission("m-rejected", "op-01"))
                    .isInstanceOf(ApiException.class)
                    .hasMessageContaining("not pending acceptance");
            verifyNoInteractions(missionPlanningService);
        }

        @Test
        @DisplayName("6. acceptMission blocks NO_SAFE_ROUTE before scheduled")
        void acceptMission_noSafeRoute_doesNotSchedule() {
            Mission mission = buildMission("m-nosafe", MissionStatus.WAITING_OPERATOR_ACCEPTANCE);
            MissionPlan plan = feasiblePlan("m-nosafe");
            plan.setFeasibilityStatus(FeasibilityStatus.NO_SAFE_ROUTE);
            when(missionRepository.findById("m-nosafe")).thenReturn(Optional.of(mission));
            when(missionOperatorAssignmentRepository.findByMissionIdAndIsCurrentTrue("m-nosafe"))
                    .thenReturn(Optional.of(operatorAssignment(mission, "op-01", "PENDING")));
            when(missionPlanningService.generateAStarEnergyAwarePlan("m-nosafe")).thenReturn(plan);

            assertThatThrownBy(() -> missionService.acceptMission("m-nosafe", "op-01"))
                    .isInstanceOf(ApiException.class)
                    .hasMessageContaining("No safe route");
            assertThat(mission.getStatus()).isEqualTo(MissionStatus.WAITING_OPERATOR_ACCEPTANCE);
            verify(missionRepository, never()).save(any());
        }

        @Test
        @DisplayName("7. acceptMission planning exception does not mark assignment accepted")
        void acceptMission_planningException_rollsBackAcceptance() {
            Mission mission = buildMission("m-plan-fail", MissionStatus.WAITING_OPERATOR_ACCEPTANCE);
            MissionOperatorAssignment assignment = operatorAssignment(mission, "op-01", "PENDING");
            when(missionRepository.findById("m-plan-fail")).thenReturn(Optional.of(mission));
            when(missionOperatorAssignmentRepository.findByMissionIdAndIsCurrentTrue("m-plan-fail"))
                    .thenReturn(Optional.of(assignment));
            when(missionPlanningService.generateAStarEnergyAwarePlan("m-plan-fail"))
                    .thenThrow(new ApiException(com.ondemandmonitoring.common.exception.ErrorCode.INVALID_REQUEST, "Mission m-plan-fail order has no target point."));

            assertThatThrownBy(() -> missionService.acceptMission("m-plan-fail", "op-01"))
                    .isInstanceOf(ApiException.class)
                    .hasMessageContaining("no target point");
            assertThat(assignment.getStatus()).isEqualTo("PENDING");
            assertThat(mission.getStatus()).isEqualTo(MissionStatus.WAITING_OPERATOR_ACCEPTANCE);
            verify(missionRepository, never()).save(any());
        }

        @Test
        @DisplayName("8. rejectMission success when WAITING_OPERATOR_ACCEPTANCE")
        void rejectMission_success() {
            Mission mission = buildMission("m-2", MissionStatus.WAITING_OPERATOR_ACCEPTANCE);
            when(missionRepository.findById("m-2")).thenReturn(Optional.of(mission));
            when(missionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            MissionResponse result = missionService.rejectMission("m-2", "op-01", "Trùng lịch cá nhân");

            assertThat(result.getStatus()).isEqualTo(MissionStatus.RESOURCE_ASSIGNING);
            assertThat(result.getRejectionReason()).isEqualTo("Trùng lịch cá nhân");
            verify(missionOperatorAssignmentRepository, atLeastOnce()).save(any());
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
        @DisplayName("1b. connectGcs blocks when no feasible plan exists")
        void connectGcs_requiresFeasiblePlan() {
            Mission mission = buildMission("m-no-plan", MissionStatus.SCHEDULED);
            when(missionRepository.findById("m-no-plan")).thenReturn(Optional.of(mission));
            when(missionPlanRepository.findByMissionId("m-no-plan")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> missionService.connectGcs("m-no-plan"))
                    .isInstanceOf(ApiException.class)
                    .hasMessageContaining("feasible plan");
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
        @DisplayName("5. runPreflightCheck HARDWARE fault routes drone to MAINTENANCE, auto-creates ticket, re-queues mission to RESOURCE_ASSIGNING")
        void runPreflightCheck_hardwareFailed_routesToMaintenance_andRequeues() {
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
            when(missionDroneAssignmentRepository.findByMissionIdAndIsCurrentTrue("m-fail")).thenReturn(Optional.empty());
            when(maintenanceTicketRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            PreflightCheckResponse result = missionService.runPreflightCheck("m-fail", "DRONE-01");

            assertThat(result.getOverallPassed()).isFalse();
            assertThat(drone.getStatus()).isEqualTo(DroneStatus.MAINTENANCE);
            // AC: HARDWARE fault auto-creates ticket and re-queues to RESOURCE_ASSIGNING for manager
            assertThat(mission.getStatus()).isEqualTo(MissionStatus.RESOURCE_ASSIGNING);
            verify(maintenanceTicketRepository).save(any());
        }

        @Test
        @DisplayName("6. runPreflightCheck BATTERY fault routes drone to IDLE_CHARGING, triggers auto-swap, re-queues mission to RESOURCE_ASSIGNING")
        void runPreflightCheck_batteryLow_routesToIdleCharging_andTriggersAutoSwap() {
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
            when(missionDroneAssignmentRepository.findByMissionIdAndIsCurrentTrue("m-bat")).thenReturn(Optional.empty());
            // No replacement available — simulates pool empty fallback
            when(droneRepository.findFirstAvailableExcluding(eq(DroneStatus.AVAILABLE), eq(drone.getId())))
                    .thenReturn(Optional.empty());

            PreflightCheckResponse result = missionService.runPreflightCheck("m-bat", "DRONE-01");

            assertThat(result.getOverallPassed()).isFalse();
            assertThat(drone.getStatus()).isEqualTo(DroneStatus.MAINTENANCE);
            verify(maintenanceTicketRepository).save(any());
            // AC: BATTERY fault auto-swap attempted, mission re-queued to RESOURCE_ASSIGNING
            assertThat(mission.getStatus()).isEqualTo(MissionStatus.RESOURCE_ASSIGNING);
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
        @DisplayName("4. completeMission throws exception when status is SCHEDULED (fully invalid for completion)")
        void completeMission_invalidStatus_throws() {
            // completeMission now accepts IN_FLIGHT, RETURNING, POSTFLIGHT_CHECKING
            // SCHEDULED is not in that set and must still throw
            Mission mission = buildMission("m-8", MissionStatus.SCHEDULED);
            when(missionRepository.findById("m-8")).thenReturn(Optional.of(mission));

            assertThatThrownBy(() -> missionService.completeMission("m-8"))
                    .isInstanceOf(ApiException.class)
                    .hasMessageContaining("IN_FLIGHT");
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
                    .hasMessageContaining("has no assigned");
        }
    }

    // =========================================================================
    // Fault Handling – Acceptance Criteria Tests (3 Test Cases)
    // =========================================================================
    @Nested
    @DisplayName("AC – Automated Preflight Fault Handling")
    class AC_PreflightFaultHandling {

        @Test
        @DisplayName("AC-1. assignDrone throws when drone is under MAINTENANCE – block by status")
        void assignDrone_maintenanceDrone_blocked() {
            Mission mission = buildMission("m-assign", MissionStatus.RESOURCE_ASSIGNING);
            Drone maintenanceDrone = buildDrone("DRONE-BROKEN", DroneStatus.MAINTENANCE);

            when(missionRepository.findById("m-assign")).thenReturn(Optional.of(mission));
            when(droneRepository.findById("DRONE-BROKEN-id")).thenReturn(Optional.of(maintenanceDrone));

            assertThatThrownBy(() -> missionService.assignDrone("m-assign", "DRONE-BROKEN-id"))
                    .isInstanceOf(ApiException.class)
                    .hasMessageContaining("MAINTENANCE");
        }

        @Test
        @DisplayName("AC-2. BATTERY preflight fault triggers auto-swap when a replacement drone is available")
        void preflightFail_battery_autoSwapSucceeds() {
            Mission mission = buildMission("m-bat-swap", MissionStatus.CONNECTED);
            Drone faultyDrone = buildDrone("DRONE-LOW", DroneStatus.PREFLIGHT);
            Drone replacementDrone = buildDrone("DRONE-GOOD", DroneStatus.AVAILABLE);
            replacementDrone.setId("DRONE-GOOD-id");

            PreflightCheck batteryCheck = new PreflightCheck();
            batteryCheck.setOverallPassed(false);
            batteryCheck.setFaultType("BATTERY");
            batteryCheck.setBatteryPercent(20.0);
            batteryCheck.setFailureReason("Battery below minimum threshold (80%)");
            batteryCheck.setDrone(faultyDrone);

            MissionPlan plan = feasiblePlan("m-bat-swap");
            when(missionPlanRepository.findByMissionId("m-bat-swap")).thenReturn(Optional.of(plan));
            when(missionRepository.findById("m-bat-swap")).thenReturn(Optional.of(mission));
            when(droneRepository.findByDroneCode("DRONE-LOW")).thenReturn(Optional.of(faultyDrone));
            when(preflightCheckService.run("DRONE-LOW", "m-bat-swap")).thenReturn(batteryCheck);
            when(droneRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(missionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(missionDroneAssignmentRepository.findByMissionIdAndIsCurrentTrue("m-bat-swap"))
                    .thenReturn(Optional.empty());
            // Auto-swap finds a replacement drone
            when(droneRepository.findFirstAvailableExcluding(eq(DroneStatus.AVAILABLE), eq(faultyDrone.getId())))
                    .thenReturn(Optional.of(replacementDrone));
            when(missionRepository.findActiveByDroneId("DRONE-GOOD-id")).thenReturn(List.of());
            when(missionDroneAssignmentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(preflightCheckMapper.toResponse(any(), any())).thenReturn(
                    com.ondemandmonitoring.drone.dto.response.PreflightCheckResponse.builder()
                            .overallPassed(false).build()
            );

            missionService.runPreflightCheck("m-bat-swap", "DRONE-LOW");

            // Faulty drone should be MAINTENANCE, replacement RESERVED, mission RESOURCE_ASSIGNING
            assertThat(faultyDrone.getStatus()).isEqualTo(DroneStatus.MAINTENANCE);
            assertThat(replacementDrone.getStatus()).isEqualTo(DroneStatus.RESERVED);
            assertThat(mission.getStatus()).isEqualTo(MissionStatus.RESOURCE_ASSIGNING);
            verify(missionDroneAssignmentRepository, atLeastOnce()).save(any());
        }

        @Test
        @DisplayName("AC-3. BATTERY preflight fault with no available drones falls back to RESOURCE_ASSIGNING")
        void preflightFail_battery_noAvailableDrone_requeues() {
            Mission mission = buildMission("m-bat-empty", MissionStatus.CONNECTED);
            Drone faultyDrone = buildDrone("DRONE-DEAD", DroneStatus.PREFLIGHT);

            PreflightCheck batteryCheck = new PreflightCheck();
            batteryCheck.setOverallPassed(false);
            batteryCheck.setFaultType("BATTERY");
            batteryCheck.setBatteryPercent(15.0);
            batteryCheck.setFailureReason("Battery critically low");
            batteryCheck.setDrone(faultyDrone);

            MissionPlan plan = feasiblePlan("m-bat-empty");
            when(missionPlanRepository.findByMissionId("m-bat-empty")).thenReturn(Optional.of(plan));
            when(missionRepository.findById("m-bat-empty")).thenReturn(Optional.of(mission));
            when(droneRepository.findByDroneCode("DRONE-DEAD")).thenReturn(Optional.of(faultyDrone));
            when(preflightCheckService.run("DRONE-DEAD", "m-bat-empty")).thenReturn(batteryCheck);
            when(droneRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(missionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(missionDroneAssignmentRepository.findByMissionIdAndIsCurrentTrue("m-bat-empty"))
                    .thenReturn(Optional.empty());
            // No available drone in pool
            when(droneRepository.findFirstAvailableExcluding(eq(DroneStatus.AVAILABLE), eq(faultyDrone.getId())))
                    .thenReturn(Optional.empty());
            when(preflightCheckMapper.toResponse(any(), any())).thenReturn(
                    com.ondemandmonitoring.drone.dto.response.PreflightCheckResponse.builder()
                            .overallPassed(false).build()
            );

            missionService.runPreflightCheck("m-bat-empty", "DRONE-DEAD");

            // Faulty drone MAINTENANCE, mission falls back to RESOURCE_ASSIGNING for manager
            assertThat(faultyDrone.getStatus()).isEqualTo(DroneStatus.MAINTENANCE);
            assertThat(mission.getStatus()).isEqualTo(MissionStatus.RESOURCE_ASSIGNING);
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

    private MissionOperatorAssignment operatorAssignment(Mission mission, String operatorId, String status) {
        MissionOperatorAssignment assignment = new MissionOperatorAssignment();
        assignment.setMission(mission);
        assignment.setOperatorId(operatorId);
        assignment.setStatus(status);
        assignment.setIsCurrent(true);
        return assignment;
    }

    private MissionPlan feasiblePlan(String missionId) {
        MissionPlan plan = new MissionPlan();
        plan.setId("plan-" + missionId);
        plan.setPlanningAlgorithm(PlanningAlgorithm.ASTAR_ENERGY_AWARE);
        plan.setFeasibilityStatus(FeasibilityStatus.FEASIBLE);
        plan.setPlannedDistanceM(218.5269119345814);
        plan.setPlannedDurationSec(119.36345596729069);
        plan.setMaxPlannedAltitudeM(19.5);
        plan.setEstimatedEnergyMah(187.97194661669408);
        plan.setEstimatedBatteryUsedPercent(3.759438932333882);
        plan.setSafetyReservePercent(20.0);
        plan.setRequiredBatteryPercent(23.759438932333882);
        plan.setPlanningTimeMs(42L);
        return plan;
    }

    private Drone buildDrone(String code, DroneStatus status) {
        Drone d = new Drone();
        d.setId(code + "-id");
        d.setDroneCode(code);
        d.setStatus(status);
        return d;
    }
}
