package com.ondemandmonitoring.mission.service.impl;

import com.ondemandmonitoring.common.exception.ApiException;
import com.ondemandmonitoring.common.exception.ErrorCode;
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
import com.ondemandmonitoring.mission.dto.response.MissionPlanResponse;
import com.ondemandmonitoring.mission.dto.response.MissionResponse;
import com.ondemandmonitoring.mission.enums.FeasibilityStatus;
import com.ondemandmonitoring.mission.enums.MissionStatus;
import com.ondemandmonitoring.mission.repository.FlightTokenRepository;
import com.ondemandmonitoring.mission.repository.MissionRepository;
import com.ondemandmonitoring.drone.mapper.PreflightCheckMapper;
import com.ondemandmonitoring.mission.mapper.FlightTokenMapper;
import com.ondemandmonitoring.mission.mapper.MissionMapper;
import com.ondemandmonitoring.mission.service.IMissionService;
import com.ondemandmonitoring.planning.service.MissionPlanningService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.ondemandmonitoring.drone.domain.MaintenanceTicket;
import com.ondemandmonitoring.drone.repository.MaintenanceTicketRepository;
import com.ondemandmonitoring.mission.domain.*;
import com.ondemandmonitoring.mission.repository.*;

/**
 * Implementation of {@link IMissionService} for mission lifecycle management.
 * Enterprise pattern: Maps entities to DTOs within @Transactional scope to guarantee safety against LazyInitializationException.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class MissionService implements IMissionService {

    static final long TOKEN_TTL_SECONDS = 900L; // 15 minutes

    MissionRepository missionRepository;
    DroneRepository droneRepository;
    FlightTokenRepository flightTokenRepository;
    PreflightCheckService preflightCheckService;
    MissionMapper missionMapper;
    FlightTokenMapper flightTokenMapper;
    PreflightCheckMapper preflightCheckMapper;

    // Supporting audit & work order repositories
    MissionDroneAssignmentRepository missionDroneAssignmentRepository;
    MissionOperatorAssignmentRepository missionOperatorAssignmentRepository;
    MissionPlanRepository missionPlanRepository;
    MissionPlanningService missionPlanningService;
    GcsSessionRepository gcsSessionRepository;
    ControlHandoverRepository controlHandoverRepository;
    PostflightCheckRepository postflightCheckRepository;
    MaintenanceTicketRepository maintenanceTicketRepository;
    com.ondemandmonitoring.order.repository.OrderRepository orderRepository;

    // =========================================================================
    // Query Methods
    // =========================================================================

    @Override
    @Transactional(readOnly = true)
    public MissionResponse getByIdResponse(String missionId) {
        return missionMapper.toResponse(getOrThrow(missionId));
    }

    @Override
    @Transactional(readOnly = true)
    public MissionResponse getByCodeResponse(String missionCode) {
        Mission mission = missionRepository.findByMissionCode(missionCode)
                .orElseThrow(() -> new ApiException(ErrorCode.MISSION_NOT_FOUND,
                        "Mission not found: " + missionCode));
        return missionMapper.toResponse(mission);
    }

    @Override
    @Transactional(readOnly = true)
    public List<MissionResponse> getByOperatorId(String operatorId) {
        List<MissionStatus> activeStatuses = List.of(MissionStatus.values());
        return missionRepository.findByOperatorIdAndStatusIn(operatorId, activeStatuses)
                .stream()
                .sorted(java.util.Comparator.comparing(
                        Mission::getScheduledStartAt,
                        java.util.Comparator.nullsLast(java.util.Comparator.reverseOrder())
                ))
                .map(missionMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public MissionPlanResponse getMissionPlan(String missionId) {
        getOrThrow(missionId);
        MissionPlan plan = missionPlanRepository.findByMissionId(missionId)
                .orElseThrow(() -> new ApiException(
                        ErrorCode.RESOURCE_NOT_FOUND,
                        "Mission plan not found for mission: " + missionId));
        return missionMapper.toPlanResponse(plan);
    }

    @Override
    @Transactional(readOnly = true)
    public Mission findById(String missionId) {
        return getOrThrow(missionId);
    }

    // =========================================================================
    // F2 – Manager Assignment (Flow 2)
    // =========================================================================

    @Override
    @Transactional
    public MissionResponse createMissionForOrder(String orderId) {
        com.ondemandmonitoring.order.domain.Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Order not found with id: " + orderId));

        Mission mission = new Mission();
        mission.setMissionCode("MS-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        mission.setStatus(MissionStatus.RESOURCE_ASSIGNING);
        mission.setOrder(order);
        
        Mission saved = missionRepository.save(mission);
        log.info("Mission created for order {}: {}", orderId, saved.getMissionCode());
        return missionMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public MissionResponse assignDrone(String missionId, String droneId) {
        Mission mission = getOrThrow(missionId);
        requireStatus(mission, MissionStatus.RESOURCE_ASSIGNING);
        
        Drone drone = droneRepository.findById(droneId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Drone not found"));
                
        if (drone.getStatus() != DroneStatus.AVAILABLE) {
            throw new ApiException(ErrorCode.DRONE_NOT_AVAILABLE, "Drone is not available");
        }
        
        // If there was an old device, release it back to AVAILABLE
        Drone oldDrone = getCurrentDrone(missionId);
        if (oldDrone != null) {
            oldDrone.setStatus(DroneStatus.AVAILABLE);
            droneRepository.save(oldDrone);
            
            // Release old MissionDroneAssignment
            missionDroneAssignmentRepository.findByMissionIdAndIsCurrentTrue(missionId)
                    .ifPresent(mda -> {
                        mda.setIsCurrent(false);
                        mda.setStatus("RELEASED");
                        mda.setReleaseReason("MANAGER_REASSIGNED");
                        mda.setReleasedAt(Instant.now());
                        missionDroneAssignmentRepository.save(mda);
                    });
        }

        drone.setStatus(DroneStatus.RESERVED);
        droneRepository.save(drone);
        
        // Record new MissionDroneAssignment
        MissionDroneAssignment newMda = new MissionDroneAssignment();
        newMda.setMission(mission);
        newMda.setDrone(drone);
        newMda.setAssignmentSource("MANUAL_MANAGER");
        newMda.setStatus("ACTIVE");
        newMda.setIsCurrent(true);
        newMda.setAssignedAt(Instant.now());
        missionDroneAssignmentRepository.save(newMda);
        
        log.info("Mission {} assigned to drone {}", missionId, droneId);
        Mission saved = missionRepository.save(mission);
        return missionMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public MissionResponse assignOperator(String missionId, String operatorId) {
        Mission mission = getOrThrow(missionId);
        requireStatus(mission, MissionStatus.RESOURCE_ASSIGNING);

        if (getCurrentDrone(missionId) == null) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "Must assign a drone before assigning an operator.");
        }
        
        // If there was an old operator, release them
        String currentOp = getCurrentOperatorId(missionId);
        if (currentOp != null && !currentOp.equals(operatorId)) {
            missionOperatorAssignmentRepository.findByMissionIdAndIsCurrentTrue(missionId)
                    .ifPresent(moa -> {
                        moa.setIsCurrent(false);
                        moa.setStatus("RELEASED");
                        moa.setReleasedAt(Instant.now());
                        missionOperatorAssignmentRepository.save(moa);
                    });
        }

        mission.setStatus(MissionStatus.WAITING_OPERATOR_ACCEPTANCE);
        
        // Record new MissionOperatorAssignment in PENDING state
        MissionOperatorAssignment newMoa = new MissionOperatorAssignment();
        newMoa.setMission(mission);
        newMoa.setOperatorId(operatorId);
        newMoa.setStatus("PENDING");
        newMoa.setIsCurrent(true);
        newMoa.setAssignedAt(Instant.now());
        missionOperatorAssignmentRepository.save(newMoa);
        
        log.info("Mission {} assigned to operator {}", missionId, operatorId);
        Mission saved = missionRepository.save(mission);
        return missionMapper.toResponse(saved);
    }

    // =========================================================================
    // F3.1 – Operator Acceptance / Rejection
    // =========================================================================

    @Override
    @Transactional
    public MissionResponse acceptMission(String missionId, String operatorId) {
        Mission mission = getOrThrow(missionId);
        requireStatus(mission, MissionStatus.WAITING_OPERATOR_ACCEPTANCE);

        MissionOperatorAssignment assignment = requireCurrentOperatorAssignment(missionId, operatorId);

        MissionPlan plan = missionPlanningService.generateAStarEnergyAwarePlan(missionId);
        if (plan.getFeasibilityStatus() != FeasibilityStatus.FEASIBLE) {
            throw new ApiException(
                    ErrorCode.INVALID_REQUEST,
                    "No safe route could be generated for this mission.");
        }

        // Update MissionOperatorAssignment audit
        assignment.setStatus("ACCEPTED");
        assignment.setRespondedAt(Instant.now());
        missionOperatorAssignmentRepository.save(assignment);

        mission.setStatus(MissionStatus.SCHEDULED);

        log.info("Mission {} accepted by operator {}", missionId, operatorId);
        Mission saved = missionRepository.save(mission);
        return missionMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public MissionResponse rejectMission(String missionId, String operatorId, String reason) {
        Mission mission = getOrThrow(missionId);
        requireStatus(mission, MissionStatus.WAITING_OPERATOR_ACCEPTANCE);

        mission.setRejectionReason(reason);
        mission.setStatus(MissionStatus.RESOURCE_ASSIGNING);

        // Update MissionOperatorAssignment rejection audit
        missionOperatorAssignmentRepository.findByMissionIdAndIsCurrentTrue(missionId)
                .ifPresentOrElse(assignment -> {
                    assignment.setStatus("REJECTED");
                    assignment.setRejectionReason(reason);
                    assignment.setIsCurrent(false);
                    assignment.setRespondedAt(Instant.now());
                    assignment.setReleasedAt(Instant.now());
                    missionOperatorAssignmentRepository.save(assignment);
                }, () -> {
                    MissionOperatorAssignment assignment = new MissionOperatorAssignment();
                    assignment.setMission(mission);
                    assignment.setOperatorId(operatorId);
                    assignment.setStatus("REJECTED");
                    assignment.setRejectionReason(reason);
                    assignment.setIsCurrent(false);
                    assignment.setRespondedAt(Instant.now());
                    assignment.setReleasedAt(Instant.now());
                    missionOperatorAssignmentRepository.save(assignment);
                });

        log.warn("Mission {} rejected by operator {} – reason: {}", missionId, operatorId, reason);
        Mission saved = missionRepository.save(mission);
        return missionMapper.toResponse(saved);
    }

    // =========================================================================
    // F3.2 – GCS Pairing & Pre-flight Gate
    // =========================================================================

    @Override
    @Transactional
    public MissionResponse connectGcs(String missionId) {
        Mission mission = getOrThrow(missionId);
        if (mission.getStatus() != MissionStatus.SCHEDULED && mission.getStatus() != MissionStatus.CONNECTED) {
            throw new ApiException(ErrorCode.MISSION_STATUS_INVALID,
                    "Mission must be in SCHEDULED state to pair with GCS, current: " + mission.getStatus());
        }
        requireFeasiblePlan(missionId);
        mission.setStatus(MissionStatus.CONNECTED);

        Drone drone = getCurrentDrone(missionId);
        if (drone != null) {
            drone.setStatus(DroneStatus.PREFLIGHT);
            droneRepository.save(drone);

            // Record GcsSession flight connection log
            GcsSession gcsSession = new GcsSession();
            gcsSession.setMission(mission);
            gcsSession.setDrone(drone);
            gcsSession.setOperatorId(getCurrentOperatorId(missionId));
            gcsSession.setConnectionStatus("CONNECTED");
            gcsSession.setTelemetryActive(true);
            gcsSession.setConnectedAt(Instant.now());
            gcsSessionRepository.save(gcsSession);

            // Ensure MissionDroneAssignment record exists
            missionDroneAssignmentRepository.findByMissionIdAndIsCurrentTrue(missionId)
                    .orElseGet(() -> {
                        MissionDroneAssignment mda = new MissionDroneAssignment();
                        mda.setMission(mission);
                        mda.setDrone(drone);
                        mda.setAssignmentSource("MANUAL_MANAGER");
                        mda.setStatus("ACTIVE");
                        mda.setIsCurrent(true);
                        mda.setAssignedAt(Instant.now());
                        return missionDroneAssignmentRepository.save(mda);
                    });
        }

        log.info("Mission {} – powerOnAndPairWithGCSApp confirmed, status CONNECTED", missionId);
        Mission saved = missionRepository.save(mission);
        return missionMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public PreflightCheckResponse runPreflightCheck(String missionId, String droneCode) {
        Mission mission = getOrThrow(missionId);
        Drone drone = droneRepository.findByDroneCode(droneCode)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Drone not found: " + droneCode));

        requireFeasiblePlan(missionId);

        if (mission.getStatus() == MissionStatus.SCHEDULED) {
            mission.setStatus(MissionStatus.CONNECTED);
        }

        PreflightCheck check = preflightCheckService.run(droneCode, missionId);
        FlightTokenResponse tokenResponse = null;

        // Store preflight check diagnostics inline on Mission entity (per DB design)
        mission.setPreflightPassed(check.getOverallPassed());
        mission.setPreflightFaultType(check.getFaultType());
        mission.setPreflightFailureReason(check.getFailureReason());
        mission.setPreflightCheckedAt(Instant.now());
        mission.setPreflightRetryCount(
                (mission.getPreflightRetryCount() == null ? 0 : mission.getPreflightRetryCount()) + 1
        );

        if (Boolean.TRUE.equals(check.getOverallPassed())) {
            mission.setStatus(MissionStatus.READY_TO_FLY);
            drone.setStatus(DroneStatus.PREFLIGHT);
            droneRepository.save(drone);

            FlightToken token = issueFlightToken(missionId, droneCode, getCurrentOperatorId(missionId));
            tokenResponse = flightTokenMapper.toResponse(token);
            log.info("Mission {} digital preflight PASSED – issued FlightToken {}", missionId, token.getTokenValue());
        } else {
            mission.setStatus(MissionStatus.FAILED_PREFLIGHT);
            handlePreflightFailure(mission, drone, check);
        }

        missionRepository.save(mission);
        return preflightCheckMapper.toResponse(check, tokenResponse);
    }

    private void handlePreflightFailure(Mission mission, Drone drone, PreflightCheck check) {
        String faultType = check.getFaultType();
        if ("HARDWARE".equalsIgnoreCase(faultType)) {
            drone.setStatus(DroneStatus.MAINTENANCE);
            log.error("SYSTEM OPERATOR ALERT: Drone {} failed pre-flight check due to HARDWARE fault ({}). Status set to MAINTENANCE.",
                    drone.getDroneCode(), check.getFailureReason());

            // Auto-create MaintenanceTicket for System Operator
            MaintenanceTicket ticket = new MaintenanceTicket();
            ticket.setTicketCode("TKT-PREFLIGHT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
            ticket.setDrone(drone);
            ticket.setReportedBy("AUTOMATED_PREFLIGHT_GATE");
            ticket.setIssueType("PREFLIGHT_HARDWARE_FAIL");
            ticket.setSeverity("HIGH");
            ticket.setDescription("Pre-flight failure on drone " + drone.getDroneCode() + ": " + check.getFailureReason());
            ticket.setStatus("OPEN");
            ticket.setOpenedAt(Instant.now());
            maintenanceTicketRepository.save(ticket);
        } else if ("BATTERY".equalsIgnoreCase(faultType)) {
            drone.setStatus(DroneStatus.IDLE_CHARGING);
            log.warn("CHARGING STATION ALERT: Drone {} failed pre-flight check due to BATTERY low ({}%). Status set to IDLE_CHARGING.",
                    drone.getDroneCode(), check.getBatteryPercent());
        } else {
            drone.setStatus(DroneStatus.MAINTENANCE);
        }
        droneRepository.save(drone);

        mission.setStatus(MissionStatus.PENDING_APPROVAL);
        log.warn("Mission {} order status re-queued to PENDING_APPROVAL for Manager re-assignment (Flow 2)", mission.getId());
    }

    private FlightToken issueFlightToken(String missionId, String droneCode, String operatorId) {
        Instant now = Instant.now();
        FlightToken token = new FlightToken();
        token.setTokenValue(com.ondemandmonitoring.mission.util.FlightTokenGenerator.generateTokenValue(missionId, droneCode, operatorId, now));
        token.setMissionId(missionId);
        token.setDroneCode(droneCode);
        token.setOperatorId(operatorId);
        token.setIssuedAt(now);
        token.setExpiresAt(now.plusSeconds(TOKEN_TTL_SECONDS));
        token.setUsed(false);
        token.setRevoked(false);
        return flightTokenRepository.save(token);
    }

    private MissionOperatorAssignment requireCurrentOperatorAssignment(String missionId, String operatorId) {
        MissionOperatorAssignment assignment = missionOperatorAssignmentRepository.findByMissionIdAndIsCurrentTrue(missionId)
                .orElseThrow(() -> new ApiException(
                        ErrorCode.INVALID_REQUEST,
                        "Mission has no current operator assignment."));
        if (assignment.getOperatorId() == null || !assignment.getOperatorId().equals(operatorId)) {
            throw new ApiException(
                    ErrorCode.INVALID_REQUEST,
                    "Mission is assigned to another operator.");
        }
        if (!"PENDING".equalsIgnoreCase(assignment.getStatus())) {
            throw new ApiException(
                    ErrorCode.INVALID_REQUEST,
                    "Mission operator assignment is not pending acceptance.");
        }
        return assignment;
    }

    private void requireFeasiblePlan(String missionId) {
        MissionPlan plan = missionPlanRepository.findByMissionId(missionId)
                .orElseThrow(() -> new ApiException(
                        ErrorCode.INVALID_REQUEST,
                        "Mission must have a feasible plan before preflight."));
        if (plan.getFeasibilityStatus() != FeasibilityStatus.FEASIBLE) {
            throw new ApiException(
                    ErrorCode.INVALID_REQUEST,
                    "No safe route could be generated for this mission.");
        }
    }

    @Override
    @Transactional
    public MissionResponse replaceDrone(String missionId, String newDroneCode) {
        Mission mission = getOrThrow(missionId);
        Drone newDrone = droneRepository.findByDroneCode(newDroneCode)
                .orElseThrow(() -> new ApiException(ErrorCode.DRONE_NOT_AVAILABLE, "Drone " + newDroneCode + " không tồn tại"));

        if (newDrone.getStatus() != DroneStatus.AVAILABLE) {
            throw new ApiException(ErrorCode.DRONE_NOT_AVAILABLE, "Drone " + newDroneCode + " is not AVAILABLE (status: " + newDrone.getStatus() + ")");
        }

        List<Mission> activeMissions = missionRepository.findActiveByDroneId(newDrone.getId());
        boolean hasConflict = activeMissions.stream().anyMatch(m -> !m.getId().equals(missionId));
        if (hasConflict) {
            throw new ApiException(ErrorCode.SCHEDULE_CONFLICT, "Drone " + newDroneCode + " đang được lên lịch cho chuyến bay khác");
        }

        Drone oldDrone = getCurrentDrone(missionId);
        if (oldDrone != null) {
            oldDrone.setStatus(DroneStatus.MAINTENANCE);
            droneRepository.save(oldDrone);

            // Release old MissionDroneAssignment
            missionDroneAssignmentRepository.findByMissionIdAndIsCurrentTrue(missionId)
                    .ifPresent(mda -> {
                        mda.setIsCurrent(false);
                        mda.setStatus("RELEASED");
                        mda.setReleaseReason("PREFLIGHT_FAIL");
                        mda.setReleasedAt(Instant.now());
                        missionDroneAssignmentRepository.save(mda);
                    });
        }

        newDrone.setStatus(DroneStatus.PREFLIGHT);
        droneRepository.save(newDrone);

        // Record new MissionDroneAssignment
        MissionDroneAssignment newMda = new MissionDroneAssignment();
        newMda.setMission(mission);
        newMda.setDrone(newDrone);
        newMda.setAssignmentSource("MANUAL_SWAP");
        newMda.setStatus("ACTIVE");
        newMda.setIsCurrent(true);
        newMda.setAssignedAt(Instant.now());
        missionDroneAssignmentRepository.save(newMda);

        mission.setStatus(MissionStatus.CONNECTED);
        log.info("Mission {} – replaced drone with {}, status reset to CONNECTED", missionId, newDroneCode);
        Mission saved = missionRepository.save(mission);
        return missionMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public MissionResponse handoverControl(String missionId, String operatorId) {
        Mission mission = getOrThrow(missionId);
        requireStatus(mission, MissionStatus.READY_TO_FLY);
        
        // Record ControlHandover audit log linked to current GcsSession
        GcsSession activeGcsSession = gcsSessionRepository
                .findTopByMissionIdAndConnectionStatusOrderByConnectedAtDesc(missionId, "CONNECTED")
                .orElse(null);

        ControlHandover handover = new ControlHandover();
        handover.setGcsSession(activeGcsSession);
        handover.setDrone(getCurrentDrone(missionId));
        handover.setOperatorId(operatorId);
        handover.setStatus("CONFIRMED");
        handover.setAcknowledgementText("Operator confirmed control handover and preflight checks before launch");
        handover.setConfirmedAt(Instant.now());
        controlHandoverRepository.save(handover);

        log.info("Mission {} – control handed over to operator {} at {}", missionId, operatorId, Instant.now());
        Mission saved = missionRepository.save(mission);
        return missionMapper.toResponse(saved);
    }

    // =========================================================================
    // F3.3 – Takeoff & Execution lifecycle
    // =========================================================================

    @Override
    @Transactional
    public MissionResponse startMission(String missionId, String tokenValue) {
        Mission mission = getOrThrow(missionId);
        requireStatus(mission, MissionStatus.READY_TO_FLY);

        if (tokenValue != null && !tokenValue.isBlank()) {
            FlightToken token = flightTokenRepository.findByTokenValue(tokenValue)
                    .orElseThrow(() -> new ApiException(ErrorCode.INVALID_REQUEST, "Invalid flight access token"));

            if (!token.isValid()) {
                token.setRevoked(true);
                flightTokenRepository.save(token);
                log.warn("Mission {} flight token EXPIRED or ALREADY USED – revoking token", missionId);
                throw new ApiException(ErrorCode.INVALID_REQUEST,
                        "Flight token is expired or revoked. Please re-run preflight check.");
            }
            token.setUsed(true);
            flightTokenRepository.save(token);
        } else {
            FlightToken token = flightTokenRepository.findByMissionIdAndUsedFalseAndRevokedFalse(missionId)
                    .orElse(null);
            if (token != null) {
                if (!token.isValid()) {
                    token.setRevoked(true);
                    flightTokenRepository.save(token);
                    throw new ApiException(ErrorCode.INVALID_REQUEST,
                            "Flight token is expired. Please re-run preflight check.");
                }
                token.setUsed(true);
                flightTokenRepository.save(token);
            }
        }

        mission.setStatus(MissionStatus.IN_FLIGHT);
        mission.setStartedAt(Instant.now());
        updateDroneStatus(mission, DroneStatus.ACTIVE_MISSION);

        log.info("Mission {} IN_FLIGHT – WebSocket telemetry and RTSP video stream OPENED", missionId);
        Mission saved = missionRepository.save(mission);
        return missionMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public MissionResponse startMission(String missionId) {
        return startMission(missionId, null);
    }

    @Override
    @Transactional
    public MissionResponse markReturning(String missionId) {
        Mission mission = getOrThrow(missionId);
        if (mission.getStatus() != MissionStatus.IN_FLIGHT && mission.getStatus() != MissionStatus.IN_PROGRESS) {
            throw new ApiException(ErrorCode.MISSION_STATUS_INVALID,
                    "Mission must be IN_FLIGHT to mark returning, current: " + mission.getStatus());
        }
        mission.setStatus(MissionStatus.RETURNING);
        updateDroneStatus(mission, DroneStatus.RETURNING);
        log.info("Mission {} – drone returning to base", missionId);
        Mission saved = missionRepository.save(mission);
        return missionMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public MissionResponse startPostflightChecking(String missionId) {
        Mission mission = getOrThrow(missionId);
        requireStatus(mission, MissionStatus.RETURNING);
        mission.setStatus(MissionStatus.POSTFLIGHT_CHECKING);
        log.info("Mission {} – post-flight inspection started", missionId);
        Mission saved = missionRepository.save(mission);
        return missionMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public MissionResponse completeMission(String missionId) {
        Mission mission = getOrThrow(missionId);
        requireStatus(mission, MissionStatus.POSTFLIGHT_CHECKING);
        mission.setStatus(MissionStatus.COMPLETED);
        mission.setCompletedAt(Instant.now());
        updateDroneStatus(mission, DroneStatus.AVAILABLE);
        log.info("Mission {} COMPLETED successfully", missionId);
        Mission saved = missionRepository.save(mission);
        return missionMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public MissionResponse failMission(String missionId, String reason) {
        Mission mission = getOrThrow(missionId);
        mission.setStatus(MissionStatus.FAILED);
        mission.setFailureReason(reason);
        updateDroneStatus(mission, DroneStatus.AVAILABLE);
        log.error("Mission {} FAILED – reason: {}", missionId, reason);
        Mission saved = missionRepository.save(mission);
        return missionMapper.toResponse(saved);
    }

    // =========================================================================
    // F3.5 – Post-flight drone status update
    // =========================================================================

    @Override
    @Transactional
    public MissionResponse updatePostFlightStatus(String missionId, DroneStatus newDroneStatus, String notes) {
        Mission mission = getOrThrow(missionId);
        Drone drone = getCurrentDrone(mission.getId());
        if (drone == null) {
            throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Mission " + missionId + " has no assigned drone");
        }
        drone.setStatus(newDroneStatus);
        droneRepository.save(drone);

        // Record PostflightCheck physical inspection
        boolean overallOk = (newDroneStatus != DroneStatus.MAINTENANCE);
        PostflightCheck postflightCheck = new PostflightCheck();
        postflightCheck.setMission(mission);
        postflightCheck.setDrone(drone);
        postflightCheck.setCheckedBy(getCurrentOperatorId(missionId));
        postflightCheck.setOverallOk(overallOk);
        postflightCheck.setFaultType(overallOk ? null : "PHYSICAL_DAMAGE");
        postflightCheck.setNotes(notes);
        postflightCheck.setCheckedAt(Instant.now());
        postflightCheckRepository.save(postflightCheck);

        // Auto-create MaintenanceTicket if drone requires maintenance post-flight
        if (newDroneStatus == DroneStatus.MAINTENANCE) {
            MaintenanceTicket ticket = new MaintenanceTicket();
            ticket.setTicketCode("TKT-POSTFLIGHT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
            ticket.setDrone(drone);
            ticket.setReportedBy(getCurrentOperatorId(missionId));
            ticket.setIssueType("POSTFLIGHT_DAMAGE");
            ticket.setSeverity("HIGH");
            ticket.setDescription("Post-flight physical inspection flagged maintenance needed for drone " + drone.getDroneCode() + ". Notes: " + notes);
            ticket.setStatus("OPEN");
            ticket.setOpenedAt(Instant.now());
            maintenanceTicketRepository.save(ticket);
            log.error("SYSTEM OPERATOR ALERT: Created MaintenanceTicket {} for drone {}", ticket.getTicketCode(), drone.getDroneCode());
        }

        if (mission.getStatus() == MissionStatus.POSTFLIGHT_CHECKING) {
            mission.setStatus(MissionStatus.COMPLETED);
            mission.setCompletedAt(Instant.now());
        }

        if (notes != null && !notes.isBlank()) {
            log.info("Mission {} post-flight notes: {}", missionId, notes);
        }
        log.info("Mission {} post-flight completed – drone {} status set to {}", missionId, drone.getDroneCode(), newDroneStatus);
        Mission saved = missionRepository.save(mission);
        return missionMapper.toResponse(saved);
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private Mission getOrThrow(String missionId) {
        return missionRepository.findById(missionId)
                .orElseThrow(() -> new ApiException(ErrorCode.MISSION_NOT_FOUND,
                        "Mission not found: " + missionId));
    }

    private void requireStatus(Mission mission, MissionStatus expected) {
        if (mission.getStatus() != expected) {
            throw new ApiException(ErrorCode.MISSION_STATUS_INVALID,
                    "Mission status must be " + expected + " but is " + mission.getStatus());
        }
    }

    private void updateDroneStatus(Mission mission, DroneStatus newStatus) {
        Drone drone = getCurrentDrone(mission.getId());
        if (drone != null) {
            drone.setStatus(newStatus);
            droneRepository.save(drone);
        }
    }

    private Drone getCurrentDrone(String missionId) {
        return missionDroneAssignmentRepository.findByMissionIdAndIsCurrentTrue(missionId)
                .map(MissionDroneAssignment::getDrone)
                .orElse(null);
    }

    private String getCurrentOperatorId(String missionId) {
        return missionOperatorAssignmentRepository.findByMissionIdAndIsCurrentTrue(missionId)
                .map(MissionOperatorAssignment::getOperatorId)
                .orElse(null);
    }
}
