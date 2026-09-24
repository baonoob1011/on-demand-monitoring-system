package com.ondemandmonitoring.mission.service.impl;

import com.ondemandmonitoring.common.exception.ApiException;
import com.ondemandmonitoring.common.api.PageResponse;
import com.ondemandmonitoring.common.exception.ErrorCode;
import com.ondemandmonitoring.drone.domain.Drone;
import com.ondemandmonitoring.drone.domain.DroneTelemetry;
import com.ondemandmonitoring.drone.domain.PreflightCheck;
import com.ondemandmonitoring.drone.dto.response.PreflightCheckResponse;
import com.ondemandmonitoring.drone.enums.DroneStatus;
import com.ondemandmonitoring.drone.enums.PreflightCheckStatus;
import com.ondemandmonitoring.drone.repository.DroneRepository;
import com.ondemandmonitoring.drone.repository.DroneTelemetryRepository;
import com.ondemandmonitoring.drone.repository.PersistedPreflightCheckRepository;
import com.ondemandmonitoring.drone.service.PreflightCheckService;
import com.ondemandmonitoring.drone.service.DroneTelemetryFreshness;
import com.ondemandmonitoring.mission.domain.ControlHandover;
import com.ondemandmonitoring.mission.domain.DeviceConnection;
import com.ondemandmonitoring.mission.domain.FlightToken;
import com.ondemandmonitoring.mission.domain.Mission;
import com.ondemandmonitoring.mission.domain.MissionOperatorAssignment;
import com.ondemandmonitoring.mission.domain.MissionPlan;
import com.ondemandmonitoring.mission.dto.response.FlightTokenResponse;
import com.ondemandmonitoring.mission.dto.response.MissionPlanResponse;
import com.ondemandmonitoring.mission.dto.response.MissionResponse;
import com.ondemandmonitoring.mission.dto.response.MissionTelemetryReadinessResponse;
import com.ondemandmonitoring.mission.dto.request.PostFlightStatusRequest;
import com.ondemandmonitoring.mission.enums.FeasibilityStatus;
import com.ondemandmonitoring.mission.enums.MissionStatus;
import com.ondemandmonitoring.mission.enums.InspectionResult;
import com.ondemandmonitoring.mission.repository.ControlHandoverRepository;
import com.ondemandmonitoring.mission.repository.DeviceConnectionRepository;
import com.ondemandmonitoring.mission.repository.FlightTokenRepository;
import com.ondemandmonitoring.mission.repository.MissionRepository;
import com.ondemandmonitoring.drone.mapper.PreflightCheckMapper;
import com.ondemandmonitoring.mission.mapper.FlightTokenMapper;
import com.ondemandmonitoring.mission.mapper.MissionMapper;
import com.ondemandmonitoring.mission.service.IDeviceConnectionService;
import com.ondemandmonitoring.mission.service.IFlightTokenService;
import com.ondemandmonitoring.mission.service.IMissionService;
import com.ondemandmonitoring.order.repository.OrderRepository;
import com.ondemandmonitoring.planning.service.MissionPlanningService;
import com.ondemandmonitoring.role.domain.RoleCode;
import com.ondemandmonitoring.user.domain.User;
import com.ondemandmonitoring.user.repository.UserRepository;
import com.ondemandmonitoring.user.service.AuthenticatedUserResolver;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    DroneTelemetryRepository droneTelemetryRepository;
    PersistedPreflightCheckRepository persistedPreflightCheckRepository;
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
    DeviceConnectionRepository deviceConnectionRepository;
    ControlHandoverRepository controlHandoverRepository;
    PostflightCheckRepository postflightCheckRepository;
    MaintenanceTicketRepository maintenanceTicketRepository;
    OrderRepository orderRepository;
    IDeviceConnectionService deviceConnectionService;
    IFlightTokenService flightTokenService;
    UserRepository userRepository;
    AuthenticatedUserResolver authenticatedUserResolver;

    // =========================================================================
    // Query Methods
    // =========================================================================

    @Override
    @Transactional(readOnly = true)
    public PageResponse<MissionResponse> searchStaffMissions(
            MissionStatus status, Instant from, Instant toExclusive, Pageable pageable) {
        Specification<Mission> criteria = (root, query, builder) -> {
            var predicates = new ArrayList<jakarta.persistence.criteria.Predicate>();
            if (status != null) predicates.add(builder.equal(root.get("status"), status));
            if (from != null) predicates.add(builder.greaterThanOrEqualTo(root.get("scheduledStartAt"), from));
            if (toExclusive != null) predicates.add(builder.lessThan(root.get("scheduledStartAt"), toExclusive));
            return builder.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
        return PageResponse.from(missionRepository.findAll(criteria, pageable).map(missionMapper::toResponse));
    }

    @Override
    @Transactional
    public MissionResponse getByIdResponse(String missionId) {
        Mission mission = getOrThrow(missionId);
        ensureAcceptedMissionPlan(mission);
        return missionMapper.toResponse(mission);
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
    public List<MissionResponse> getCurrentOperatorMissions() {
        return getByOperatorId(currentOperatorId());
    }

    @Override
    @Transactional(readOnly = true)
    public List<MissionResponse> getPendingAssignmentMissions() {
        return missionRepository.findByStatusIn(List.of(
                        MissionStatus.CREATED,
                        MissionStatus.RESOURCE_ASSIGNING))
                .stream()
                .sorted(java.util.Comparator.comparing(
                        Mission::getCreatedAt,
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

        // ACCEPTANCE CRITERIA: Block drones under MAINTENANCE from being assigned to any mission
        if (drone.getStatus() == DroneStatus.MAINTENANCE) {
            throw new ApiException(ErrorCode.DRONE_NOT_AVAILABLE,
                    "Drone [" + drone.getDroneCode() + "] is under MAINTENANCE and cannot be assigned to a mission.");
        }

        if (drone.getStatus() != DroneStatus.AVAILABLE) {
            throw new ApiException(ErrorCode.DRONE_NOT_AVAILABLE,
                    "Drone [" + drone.getDroneCode() + "] is not AVAILABLE (current status: " + drone.getStatus() + ")");
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
        missionOperatorAssignmentRepository.findByMissionIdAndIsCurrentTrue(missionId)
                .ifPresent(moa -> {
                    moa.setIsCurrent(false);
                    moa.setStatus("RELEASED");
                    moa.setReleasedAt(Instant.now());
                    missionOperatorAssignmentRepository.save(moa);
                });

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

    @Override
    @Transactional
    public MissionResponse assignResources(String missionId, String droneId, String operatorId) {
        validateOperator(operatorId);
        assignDrone(missionId, droneId);
        return assignOperator(missionId, operatorId);
    }

    @Override
    @Transactional
    public MissionResponse acceptCurrentOperatorMission(String missionId) {
        return acceptMission(missionId, currentOperatorId());
    }

    @Override
    @Transactional
    public MissionResponse rejectCurrentOperatorMission(String missionId, String reason) {
        return rejectMission(missionId, currentOperatorId(), reason);
    }

    @Override
    @Transactional
    public MissionResponse handoverCurrentOperatorControl(String missionId) {
        return handoverControl(missionId, currentOperatorId());
    }

    // =========================================================================
    // F3.1 – Operator Acceptance / Rejection
    // =========================================================================

    @Override
    @Transactional
    public MissionResponse acceptMission(String missionId, String operatorId) {
        Mission mission = missionRepository.findByIdForUpdate(missionId)
                .orElseThrow(() -> new ApiException(ErrorCode.MISSION_NOT_FOUND,
                        "Mission not found: " + missionId));
        MissionOperatorAssignment assignment = findCurrentOperatorAssignment(missionId, operatorId);

        if (mission.getStatus() == MissionStatus.SCHEDULED && "ACCEPTED".equals(assignment.getStatus())) {
            ensureScheduledStart(mission);
            ensureAcceptedMissionPlan(mission);
            return missionMapper.toResponse(mission);
        }
        requireStatus(mission, MissionStatus.WAITING_OPERATOR_ACCEPTANCE);
        requirePendingAssignment(assignment);

        // Update MissionOperatorAssignment audit
        assignment.setStatus("ACCEPTED");
        assignment.setRespondedAt(Instant.now());
        missionOperatorAssignmentRepository.save(assignment);

        mission.setStatus(MissionStatus.SCHEDULED);
        ensureScheduledStart(mission);
        ensureAcceptedMissionPlan(mission);

        log.info("Mission {} accepted by operator {}", missionId, operatorId);
        Mission saved = missionRepository.save(mission);
        return missionMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public MissionResponse rejectMission(String missionId, String operatorId, String reason) {
        Mission mission = getOrThrow(missionId);
        requireStatus(mission, MissionStatus.WAITING_OPERATOR_ACCEPTANCE);
        requireCurrentOperatorAssignment(missionId, operatorId);

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
        return deviceConnectionService.connectGcs(missionId);
    }

    @Override
    @Transactional(readOnly = true)
    public MissionTelemetryReadinessResponse getTelemetryReadiness(String missionId) {
        getOrThrow(missionId);
        Drone assignedDrone = getCurrentDevice(missionId);
        if (assignedDrone == null) {
            throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Mission has no assigned drone");
        }

        String droneCode = assignedDrone.getDroneCode();
        DroneTelemetry telemetry = droneTelemetryRepository.readByDroneCode(droneCode).orElse(null);
        Instant updatedAt = telemetry != null ? telemetry.getUpdatedAt() : null;
        boolean ready = telemetry != null
                && Boolean.TRUE.equals(telemetry.getConnected())
                && DroneTelemetryFreshness.isFresh(updatedAt);
        return new MissionTelemetryReadinessResponse(droneCode, ready, updatedAt);
    }

    @Override
    @Transactional
    public MissionResponse disconnectGcs(String missionId, String disconnectReason) {
        return deviceConnectionService.disconnectGcs(missionId, disconnectReason);
    }

    @Override
    @Transactional
    public MissionResponse handleGcsSessionLost(String missionId, String reason) {
        return deviceConnectionService.handleGcsSessionLost(missionId, reason);
    }

    @Override
    @Transactional
    public PreflightCheckResponse runPreflightCheck(String missionId, String droneCode) {
        Mission mission = getOrThrow(missionId);
        Drone device = droneRepository.findByDroneCode(droneCode)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Device not found: " + droneCode));

        if (mission.getStatus() != MissionStatus.CONNECTED) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "Connect GCS and bind the mission before preflight.");
        }
        Drone assignedDrone = getCurrentDevice(missionId);
        if (assignedDrone == null || !droneCode.equals(assignedDrone.getDroneCode())) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "Preflight drone does not match the mission assignment.");
        }
        // Lock the telemetry row before planning. Planning and the checklist read this same
        // entity again; a concurrent telemetry update between an unlocked read and a locked
        // read would put conflicting versions of it in this persistence context.
        DroneTelemetry telemetry = droneTelemetryRepository.findByDroneCode(droneCode).orElse(null);
        boolean freshTelemetry = telemetry != null
                && Boolean.TRUE.equals(telemetry.getConnected())
                && DroneTelemetryFreshness.isFresh(telemetry.getUpdatedAt());
        boolean persistedPreflightPassed = hasRecentPersistedPreflightPass(missionId);
        boolean livePreflightAvailable = freshTelemetry && hasValidBatteryTelemetry(telemetry);
        if (!livePreflightAvailable && !persistedPreflightPassed) {
            throw new ApiException(ErrorCode.INVALID_REQUEST,
                    "Fresh drone telemetry or a completed runtime preflight PASS is required before preflight.");
        }

        MissionPlan plan = missionPlanningService.generateAStarEnergyAwarePlan(missionId);
        requireFeasiblePlan(plan, persistedPreflightPassed);

        PreflightCheck check = livePreflightAvailable
                ? preflightCheckService.run(droneCode, missionId)
                : passedPersistedPreflightCheck(device, telemetry, missionId);
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
            device.setStatus(DroneStatus.PREFLIGHT);
            droneRepository.save(device);

            FlightToken token = issueFlightToken(missionId, droneCode, getCurrentOperatorId(missionId));
            tokenResponse = flightTokenMapper.toResponse(token);
            log.info("Mission {} digital preflight PASSED – issued FlightToken {}", missionId, token.getTokenValue());
        } else {
            mission.setStatus(MissionStatus.FAILED_PREFLIGHT);
            handlePreflightFailure(mission, device, check);
        }

        missionRepository.save(mission);
        return preflightCheckMapper.toResponse(check, tokenResponse);
    }

    private boolean hasValidBatteryTelemetry(DroneTelemetry telemetry) {
        if (telemetry == null) return false;
        Double batteryPercent = telemetry.getBatteryPercent();
        return batteryPercent != null
                && Double.isFinite(batteryPercent)
                && batteryPercent >= 0
                && batteryPercent <= 100;
    }

    private boolean hasRecentPersistedPreflightPass(String missionId) {
        return persistedPreflightCheckRepository.findFirstByMissionIdOrderByCreatedAtDesc(missionId)
                .filter(run -> run.getStatus() == PreflightCheckStatus.PASSED)
                .filter(run -> run.getCompletedAt() == null
                        || Duration.between(run.getCompletedAt(), Instant.now()).abs().toMinutes() <= 10)
                .isPresent();
    }

    private PreflightCheck passedPersistedPreflightCheck(Drone drone, DroneTelemetry telemetry, String missionId) {
        PreflightCheck check = new PreflightCheck();
        check.setDrone(drone);
        check.setMissionId(missionId);
        check.setOverallPassed(true);
        check.setConnected(true);
        check.setInAir(false);
        check.setBatteryPercent(telemetry != null ? telemetry.getBatteryPercent() : null);
        check.setGpsFixType(telemetry != null ? telemetry.getGpsFixType() : null);
        check.setGpsSatelliteCount(telemetry != null ? telemetry.getGpsSatelliteCount() : null);
        check.setGyrometerOk(true);
        check.setAccelerometerOk(true);
        check.setMagnetometerOk(true);
        check.setLocalPositionOk(true);
        check.setGlobalPositionOk(true);
        check.setHomePositionOk(true);
        check.setArmable(true);
        check.setCheckedAt(Instant.now());
        return check;
    }

    private void handlePreflightFailure(Mission mission, Drone faultyDevice, PreflightCheck check) {
        String faultType = check.getFaultType();

        if ("HARDWARE".equalsIgnoreCase(faultType)) {
            // ── HARDWARE fault: device goes to MAINTENANCE, auto-generate ticket ──────────
            faultyDevice.setStatus(DroneStatus.MAINTENANCE);
            droneRepository.save(faultyDevice);
            log.error("[PREFLIGHT-GATE] Device {} HARDWARE fault: {}. Status → MAINTENANCE.",
                    faultyDevice.getDroneCode(), check.getFailureReason());

            // ACCEPTANCE CRITERIA: Auto-create MaintenanceTicket on HARDWARE fault
            MaintenanceTicket ticket = new MaintenanceTicket();
            ticket.setTicketCode("TKT-PREFLIGHT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
            ticket.setDevice(faultyDevice); // device field — device-type agnostic
            ticket.setReportedBy("AUTOMATED_PREFLIGHT_GATE");
            ticket.setIssueType("PREFLIGHT_HARDWARE_FAIL");
            ticket.setSeverity("HIGH");
            ticket.setDescription(
                    "Pre-flight hardware failure on device " + faultyDevice.getDroneCode() + ": " + check.getFailureReason());
            ticket.setStatus("OPEN");
            ticket.setOpenedAt(Instant.now());
            maintenanceTicketRepository.save(ticket);
            log.error("[MAINTENANCE] Auto-created ticket {} for device {}", ticket.getTicketCode(), faultyDevice.getDroneCode());

            // Re-queue mission for manager to assign a new device
            mission.setStatus(MissionStatus.RESOURCE_ASSIGNING);
            releaseFaultyDeviceAssignment(mission, faultyDevice, "HARDWARE_FAIL");
            log.warn("[MISSION-REQUEUE] Mission {} → RESOURCE_ASSIGNING (hardware fault, needs manager reassignment)", mission.getId());

        } else if ("BATTERY".equalsIgnoreCase(faultType)) {
            // ── BATTERY fault: device goes to MAINTENANCE, auto-generate ticket & re-queue for Manager ─
            faultyDevice.setStatus(DroneStatus.MAINTENANCE);
            droneRepository.save(faultyDevice);
            log.warn("[PREFLIGHT-GATE] Device {} BATTERY low/failed ({}%). Status → MAINTENANCE.",
                    faultyDevice.getDroneCode(), check.getBatteryPercent());

            // Auto-create MaintenanceTicket for battery fault
            MaintenanceTicket ticket = new MaintenanceTicket();
            ticket.setTicketCode("TKT-PREFLIGHT-BAT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
            ticket.setDevice(faultyDevice);
            ticket.setReportedBy("AUTOMATED_PREFLIGHT_GATE");
            ticket.setIssueType("PREFLIGHT_BATTERY_FAIL");
            ticket.setSeverity("HIGH");
            ticket.setDescription("Pre-flight battery failure on device " + faultyDevice.getDroneCode() + ": battery level " + check.getBatteryPercent() + "%");
            ticket.setStatus("OPEN");
            ticket.setOpenedAt(Instant.now());
            maintenanceTicketRepository.save(ticket);
            log.error("[MAINTENANCE] Auto-created battery ticket {} for device {}", ticket.getTicketCode(), faultyDevice.getDroneCode());

            // Flow update: Re-queue mission to RESOURCE_ASSIGNING for Manager manual drone selection & reassignment
            mission.setStatus(MissionStatus.RESOURCE_ASSIGNING);
            releaseFaultyDeviceAssignment(mission, faultyDevice, "BATTERY_FAIL");
            log.warn("[MISSION-REQUEUE] Mission {} → RESOURCE_ASSIGNING (battery fault, needs manager reassignment)", mission.getId());

        } else {
            // Unknown fault type — treat as HARDWARE for safety
            faultyDevice.setStatus(DroneStatus.MAINTENANCE);
            droneRepository.save(faultyDevice);
            mission.setStatus(MissionStatus.RESOURCE_ASSIGNING);
            releaseFaultyDeviceAssignment(mission, faultyDevice, "UNKNOWN_FAULT");
            log.error("[PREFLIGHT-GATE] Device {} unknown fault type '{}' – defaulting to MAINTENANCE.",
                    faultyDevice.getDroneCode(), faultType);
        }
    }

    /**
     * Releases the current MissionDroneAssignment for the faulty device.
     * Does NOT alter the device's status (already set by caller).
     */
    private void releaseFaultyDeviceAssignment(Mission mission, Drone faultyDevice, String releaseReason) {
        missionDroneAssignmentRepository.findByMissionIdAndIsCurrentTrue(mission.getId())
                .ifPresent(mda -> {
                    mda.setIsCurrent(false);
                    mda.setStatus("RELEASED");
                    mda.setReleaseReason(releaseReason);
                    mda.setReleasedAt(Instant.now());
                    missionDroneAssignmentRepository.save(mda);
                });
    }

    private void releaseFaultyDroneAssignment(Mission mission, Drone faultyDrone, String releaseReason) {
        releaseFaultyDeviceAssignment(mission, faultyDrone, releaseReason);
    }

    /**
     * BATTERY fault auto-swap: find the next AVAILABLE device from the pool and assign it.
     * If none available, fall back to RESOURCE_ASSIGNING for manager intervention.
     */
    private void attemptAutoSwapDevice(Mission mission, Drone faultyDevice) {
        String missionId = mission.getId();

        // Release current assignment first
        releaseFaultyDeviceAssignment(mission, faultyDevice, "BATTERY_FAIL");

        // Try to find a replacement from the available pool
        droneRepository.findFirstAvailableExcluding(DroneStatus.AVAILABLE, faultyDevice.getId())
                .ifPresentOrElse(replacementDevice -> {
                    // Check for scheduling conflict
                    boolean hasConflict = missionRepository.findActiveByDroneId(replacementDevice.getId())
                            .stream().anyMatch(m -> !m.getId().equals(missionId));

                    if (hasConflict) {
                        log.warn("[AUTO-SWAP] Candidate device {} has a schedule conflict – falling back to RESOURCE_ASSIGNING.",
                                replacementDevice.getDroneCode());
                        mission.setStatus(MissionStatus.RESOURCE_ASSIGNING);
                        return;
                    }

                    // Assign replacement device
                    replacementDevice.setStatus(DroneStatus.RESERVED);
                    droneRepository.save(replacementDevice);

                    MissionDroneAssignment newMda = new MissionDroneAssignment();
                    newMda.setMission(mission);
                    newMda.setDrone(replacementDevice);
                    newMda.setAssignmentSource("AUTO_SYSTEM");
                    newMda.setStatus("ACTIVE");
                    newMda.setIsCurrent(true);
                    newMda.setAssignedAt(Instant.now());
                    missionDroneAssignmentRepository.save(newMda);

                    // Mission returns to RESOURCE_ASSIGNING so manager can confirm before re-dispatch
                    mission.setStatus(MissionStatus.RESOURCE_ASSIGNING);
                    log.info("[AUTO-SWAP] Mission {} – swapped faulty device {} → replacement device {} (AUTO_SYSTEM). Mission → RESOURCE_ASSIGNING.",
                            missionId, faultyDevice.getDroneCode(), replacementDevice.getDroneCode());

                }, () -> {
                    // No available replacement in the pool
                    mission.setStatus(MissionStatus.RESOURCE_ASSIGNING);
                    log.warn("[AUTO-SWAP] Mission {} – no AVAILABLE devices in pool. Mission → RESOURCE_ASSIGNING for manager intervention.", missionId);
                });
    }

    private void attemptAutoSwapDrone(Mission mission, Drone faultyDrone) {
        attemptAutoSwapDevice(mission, faultyDrone);
    }

    private FlightToken issueFlightToken(String missionId, String droneCode, String operatorId) {
        return flightTokenService.issueFlightToken(missionId, droneCode, operatorId);
    }

    private MissionOperatorAssignment requireCurrentOperatorAssignment(String missionId, String operatorId) {
        MissionOperatorAssignment assignment = findCurrentOperatorAssignment(missionId, operatorId);
        requirePendingAssignment(assignment);
        return assignment;
    }

    private MissionOperatorAssignment findCurrentOperatorAssignment(String missionId, String operatorId) {
        MissionOperatorAssignment assignment = missionOperatorAssignmentRepository.findByMissionIdAndIsCurrentTrue(missionId)
                .orElseThrow(() -> new ApiException(
                        ErrorCode.INVALID_REQUEST,
                        "Mission has no current operator assignment."));
        if (assignment.getOperatorId() == null || !assignment.getOperatorId().equals(operatorId)) {
            throw new ApiException(
                    ErrorCode.INVALID_REQUEST,
                    "Mission is assigned to another operator.");
        }
        return assignment;
    }

    private void requirePendingAssignment(MissionOperatorAssignment assignment) {
        if (!"PENDING".equalsIgnoreCase(assignment.getStatus())) {
            throw new ApiException(
                    ErrorCode.INVALID_REQUEST,
                    "Mission operator assignment is not pending acceptance.");
        }
    }

    private void requireFeasiblePlan(MissionPlan plan) {
        requireFeasiblePlan(plan, false);
    }

    private void requireFeasiblePlan(MissionPlan plan, boolean allowRuntimePreflightBatteryFallback) {
        FeasibilityStatus status = plan.getFeasibilityStatus();
        if (status == FeasibilityStatus.FEASIBLE) {
            return;
        }
        if (allowRuntimePreflightBatteryFallback && status == FeasibilityStatus.BATTERY_DATA_UNAVAILABLE) {
            return;
        }
        if (status == null) {
            throw new ApiException(ErrorCode.INVALID_REQUEST,
                    "Mission plan feasibility could not be determined.");
        }
        String message = switch (status) {
            case BATTERY_DATA_UNAVAILABLE -> "Drone battery telemetry is unavailable. Connect the drone and refresh telemetry before preflight.";
            case INSUFFICIENT_BATTERY -> "Drone battery is insufficient for this mission and its safety reserve.";
            case INVALID_TARGET -> "Mission target is invalid for route planning.";
            case NO_SAFE_ROUTE -> "No safe route could be generated for this mission.";
            default -> "Mission plan feasibility could not be determined.";
        };
        throw new ApiException(ErrorCode.INVALID_REQUEST, message);
    }

    private void ensureScheduledStart(Mission mission) {
        if (mission.getScheduledStartAt() != null || mission.getOrder() == null) {
            return;
        }

        LocalDate date = mission.getOrder().getPreferredDateFrom();
        LocalTime time = mission.getOrder().getPreferredTime() != null
                ? mission.getOrder().getPreferredTime().getStartTime()
                : null;
        if (date == null || time == null) {
            return;
        }

        mission.setScheduledStartAt(date.atTime(time)
                .atZone(ZoneId.of("Asia/Ho_Chi_Minh"))
                .toInstant());
    }

    private void ensureAcceptedMissionPlan(Mission mission) {
        if (mission == null || mission.getId() == null) {
            return;
        }
        if (mission.getStatus() == MissionStatus.CREATED
                || mission.getStatus() == MissionStatus.RESOURCE_ASSIGNING
                || mission.getStatus() == MissionStatus.WAITING_OPERATOR_ACCEPTANCE
                || mission.getStatus() == MissionStatus.CANCELLED) {
            return;
        }
        if (getCurrentDrone(mission.getId()) == null || missionPlanRepository.findByMissionId(mission.getId()).isPresent()) {
            return;
        }

        missionPlanningService.generateAStarEnergyAwarePlan(mission.getId());
    }

    @Override
    @Transactional
    public MissionResponse replaceDrone(String missionId, String newDroneCode) {
        Mission mission = getOrThrow(missionId);
        Drone newDevice = droneRepository.findByDroneCode(newDroneCode)
                .orElseThrow(() -> new ApiException(ErrorCode.DRONE_NOT_AVAILABLE, "Device " + newDroneCode + " không tồn tại"));

        if (newDevice.getStatus() != DroneStatus.AVAILABLE) {
            throw new ApiException(ErrorCode.DRONE_NOT_AVAILABLE, "Device " + newDroneCode + " is not AVAILABLE (status: " + newDevice.getStatus() + ")");
        }

        List<Mission> activeMissions = missionRepository.findActiveByDroneId(newDevice.getId());
        boolean hasConflict = activeMissions.stream().anyMatch(m -> !m.getId().equals(missionId));
        if (hasConflict) {
            throw new ApiException(ErrorCode.SCHEDULE_CONFLICT, "Device " + newDroneCode + " đang được lên lịch cho chuyến bay khác");
        }

        Drone oldDevice = getCurrentDevice(missionId);
        if (oldDevice != null) {
            oldDevice.setStatus(DroneStatus.MAINTENANCE);
            droneRepository.save(oldDevice);

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

        newDevice.setStatus(DroneStatus.PREFLIGHT);
        droneRepository.save(newDevice);

        // Record new MissionDroneAssignment
        MissionDroneAssignment newMda = new MissionDroneAssignment();
        newMda.setMission(mission);
        newMda.setDrone(newDevice);
        newMda.setAssignmentSource("MANUAL_SWAP");
        newMda.setStatus("ACTIVE");
        newMda.setIsCurrent(true);
        newMda.setAssignedAt(Instant.now());
        missionDroneAssignmentRepository.save(newMda);

        mission.setStatus(MissionStatus.CONNECTED);
        log.info("Mission {} – replaced device with {}, status reset to CONNECTED", missionId, newDroneCode);
        Mission saved = missionRepository.save(mission);
        return missionMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public MissionResponse handoverControl(String missionId, String operatorId) {
        Mission mission = getOrThrow(missionId);
        requireStatus(mission, MissionStatus.READY_TO_FLY);

        // Record ControlHandover audit log linked to current DeviceConnection
        DeviceConnection activeConnection = deviceConnectionRepository
                .findTopByMissionIdAndConnectionStatusOrderByConnectedAtDesc(missionId, "CONNECTED")
                .orElse(null);

        ControlHandover handover = new ControlHandover();
        handover.setDeviceConnection(activeConnection);
        handover.setDrone(getCurrentDevice(missionId));
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
        updateDeviceStatus(mission, DroneStatus.ACTIVE_MISSION);

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
        updateDeviceStatus(mission, DroneStatus.RETURNING);
        log.info("Mission {} – device returning to base", missionId);
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
        if (mission.getStatus() == MissionStatus.COMPLETED
                && postflightCheckRepository.findTopByMissionIdOrderByCheckedAtDesc(missionId).isPresent()) {
            return missionMapper.toResponse(mission);
        }
        if (mission.getStatus() != MissionStatus.POSTFLIGHT_CHECKING) {
            throw new ApiException(ErrorCode.MISSION_STATUS_INVALID,
                    "Mission must be POSTFLIGHT_CHECKING with a recorded inspection to complete but is " + mission.getStatus());
        }
        if (postflightCheckRepository.findTopByMissionIdOrderByCheckedAtDesc(missionId).isEmpty()) {
            throw new ApiException(ErrorCode.MISSION_STATUS_INVALID,
                    "A recorded post-flight inspection is required before mission completion");
        }
        mission.setStatus(MissionStatus.COMPLETED);
        mission.setCompletedAt(Instant.now());
        releaseMissionResources(mission, "MISSION_COMPLETE");
        log.info("Mission {} COMPLETED successfully", missionId);
        Mission saved = missionRepository.save(mission);
        return missionMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public MissionResponse failMission(String missionId, String reason) {
        Mission mission = getOrThrow(missionId);
        if (mission.getStatus() == MissionStatus.IN_FLIGHT
                || mission.getStatus() == MissionStatus.RETURNING) {
            updateDroneStatus(mission, DroneStatus.MAINTENANCE);
        }
        mission.setStatus(MissionStatus.FAILED);
        mission.setFailureReason(reason);
        releaseMissionResources(mission, "MISSION_FAILED");
        log.error("Mission {} FAILED – reason: {}", missionId, reason);
        Mission saved = missionRepository.save(mission);
        return missionMapper.toResponse(saved);
    }

    // =========================================================================
    // F3.5 – Post-flight device status update
    // =========================================================================

    @Override
    @Transactional
    public MissionResponse updatePostFlightStatus(String missionId, DroneStatus newDroneStatus, String notes) {
        return savePostFlightStatus(missionId, newDroneStatus, notes, Map.of());
    }

    @Override
    @Transactional
    public MissionResponse recordPostFlightInspection(String missionId, DroneStatus newDroneStatus,
                                                      String notes, Map<String, InspectionResult> results,
                                                      PostFlightStatusRequest.TelemetrySnapshot telemetrySnapshot) {
        Set<String> required = Set.of("a1", "a2", "p1", "p2", "e1", "e2", "e3", "e4", "d1");
        if (results == null || !results.keySet().equals(required)
                || results.values().stream().anyMatch(value -> value == null)) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "All nine inspection items must have a result");
        }
        boolean faultDetected = results.containsValue(InspectionResult.FAIL);
        DroneStatus expected = faultDetected ? DroneStatus.MAINTENANCE : DroneStatus.AVAILABLE;
        if (newDroneStatus != expected) {
            throw new ApiException(ErrorCode.INVALID_REQUEST,
                    "Drone status does not match the submitted inspection results");
        }
        Mission mission = getOrThrow(missionId);
        requireStatus(mission, MissionStatus.POSTFLIGHT_CHECKING);
        String summary = results.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(java.util.stream.Collectors.joining(", "));
        String auditNotes = "Inspection: " + summary + ". " + (notes == null ? "" : notes.strip());
        return savePostFlightStatus(missionId, newDroneStatus, auditNotes, results, telemetrySnapshot);
    }

    private MissionResponse savePostFlightStatus(String missionId, DroneStatus newDroneStatus,
                                                 String notes, Map<String, InspectionResult> results) {
        return savePostFlightStatus(missionId, newDroneStatus, notes, results, null);
    }

    private MissionResponse savePostFlightStatus(String missionId, DroneStatus newDroneStatus,
                                                 String notes, Map<String, InspectionResult> results,
                                                 PostFlightStatusRequest.TelemetrySnapshot telemetrySnapshot) {
        Mission mission = getOrThrow(missionId);
        Drone device = getCurrentDevice(mission.getId());
        if (device == null) {
            throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Mission " + missionId + " has no assigned device");
        }
        device.setStatus(newDroneStatus);
        droneRepository.save(device);

        // Record PostflightCheck physical inspection
        boolean overallOk = (newDroneStatus != DroneStatus.MAINTENANCE);
        PostflightCheck postflightCheck = new PostflightCheck();
        postflightCheck.setMission(mission);
        postflightCheck.setDrone(device);
        postflightCheck.setCheckedBy(getCurrentOperatorId(missionId));
        postflightCheck.setOverallOk(overallOk);
        if (!results.isEmpty()) {
            postflightCheck.setPhysicalConditionOk(passed(results, "a1", "a2", "e3"));
            postflightCheck.setMotorOk(passed(results, "p1", "p2"));
            postflightCheck.setBatteryOk(passed(results, "e1", "e4"));
            postflightCheck.setCameraOk(passed(results, "e2"));
            postflightCheck.setCommunicationOk(passed(results, "d1"));
        }
        if (telemetrySnapshot != null) {
            postflightCheck.setLandingBatteryPercent(telemetrySnapshot.getBatteryPercent());
            postflightCheck.setLandingBatteryState(telemetrySnapshot.getBatteryState());
            postflightCheck.setLandingAltitudeM(telemetrySnapshot.getAltitudeM());
            postflightCheck.setLandingSpeedMps(telemetrySnapshot.getSpeedMps());
            postflightCheck.setLandingHeadingDeg(telemetrySnapshot.getHeadingDeg());
            postflightCheck.setLandingTelemetryOnline(telemetrySnapshot.getOnline());
        }
        postflightCheck.setFaultType(overallOk ? null : "PHYSICAL_DAMAGE");
        postflightCheck.setNotes(notes);
        postflightCheck.setCheckedAt(Instant.now());
        postflightCheckRepository.save(postflightCheck);

        // Auto-create MaintenanceTicket if device requires maintenance post-flight
        if (newDroneStatus == DroneStatus.MAINTENANCE) {
            MaintenanceTicket ticket = new MaintenanceTicket();
            ticket.setTicketCode("TKT-POSTFLIGHT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
            ticket.setDevice(device); // device field — device-type agnostic
            ticket.setReportedBy(getCurrentOperatorId(missionId));
            ticket.setIssueType("POSTFLIGHT_DAMAGE");
            ticket.setSeverity("HIGH");
            ticket.setDescription("Post-flight physical inspection flagged maintenance needed for device " + device.getDroneCode() + ". Notes: " + notes);
            ticket.setStatus("OPEN");
            ticket.setOpenedAt(Instant.now());
            maintenanceTicketRepository.save(ticket);
            log.error("[MAINTENANCE] Auto-created MaintenanceTicket {} for device {}", ticket.getTicketCode(), device.getDroneCode());
        }

        if (mission.getStatus() == MissionStatus.POSTFLIGHT_CHECKING) {
            mission.setStatus(MissionStatus.COMPLETED);
            mission.setCompletedAt(Instant.now());
            releaseMissionResources(mission, "MISSION_COMPLETE");
        }

        if (notes != null && !notes.isBlank()) {
            log.info("Mission {} post-flight notes: {}", missionId, notes);
        }
        log.info("Mission {} post-flight completed – device {} status set to {}", missionId, device.getDroneCode(), newDroneStatus);
        Mission saved = missionRepository.save(mission);
        return missionMapper.toResponse(saved);
    }

    private boolean passed(Map<String, InspectionResult> results, String... keys) {
        for (String key : keys) {
            if (results.get(key) == InspectionResult.FAIL) {
                return false;
            }
        }
        return true;
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private Mission getOrThrow(String missionId) {
        return missionRepository.findById(missionId)
                .orElseThrow(() -> new ApiException(ErrorCode.MISSION_NOT_FOUND,
                        "Mission not found: " + missionId));
    }

    private String currentOperatorId() {
        User operator = authenticatedUserResolver.getCurrentUser();
        if (operator.getRole() == null || operator.getRole().getCode() != RoleCode.DRONE_OPERATOR) {
            throw new ApiException(ErrorCode.ACCESS_DENIED, "A drone operator account is required");
        }
        return operator.getId().toString();
    }

    private void validateOperator(String operatorId) {
        User operator = userRepository.findById(operatorId)
                .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND, "Operator not found: " + operatorId));
        if (operator.getRole() == null || operator.getRole().getCode() != RoleCode.DRONE_OPERATOR) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "Selected user is not a drone operator");
        }
        if (!Boolean.TRUE.equals(operator.getIsActive())) {
            throw new ApiException(ErrorCode.ACCOUNT_DISABLED, "Selected operator account is inactive");
        }
    }

    private void requireStatus(Mission mission, MissionStatus expected) {
        if (mission.getStatus() != expected) {
            throw new ApiException(ErrorCode.MISSION_STATUS_INVALID,
                    "Mission status must be " + expected + " but is " + mission.getStatus());
        }
    }

    private void updateDeviceStatus(Mission mission, DroneStatus newStatus) {
        Drone device = getCurrentDevice(mission.getId());
        if (device != null) {
            device.setStatus(newStatus);
            droneRepository.save(device);
        }
    }

    private void updateDroneStatus(Mission mission, DroneStatus newStatus) {
        updateDeviceStatus(mission, newStatus);
    }

    private void releaseMissionResources(Mission mission, String reason) {
        Drone device = getCurrentDevice(mission.getId());
        if (device != null) {
            if (device.getStatus() != DroneStatus.MAINTENANCE) {
                device.setStatus(DroneStatus.AVAILABLE);
            }
            droneRepository.save(device);
        }

        missionDroneAssignmentRepository.findByMissionIdAndIsCurrentTrue(mission.getId())
                .ifPresent(assignment -> {
                    assignment.setIsCurrent(false);
                    assignment.setStatus("RELEASED");
                    assignment.setReleaseReason(reason);
                    assignment.setReleasedAt(Instant.now());
                    missionDroneAssignmentRepository.save(assignment);
                });

        missionOperatorAssignmentRepository.findByMissionIdAndIsCurrentTrue(mission.getId())
                .ifPresent(assignment -> {
                    assignment.setIsCurrent(false);
                    assignment.setStatus("COMPLETED");
                    assignment.setReleasedAt(Instant.now());
                    if (assignment.getRespondedAt() == null) {
                        assignment.setRespondedAt(Instant.now());
                    }
                    missionOperatorAssignmentRepository.save(assignment);
                });
    }

    private Drone getCurrentDevice(String missionId) {
        return missionDroneAssignmentRepository.findByMissionIdAndIsCurrentTrue(missionId)
                .map(MissionDroneAssignment::getDrone)
                .orElse(null);
    }

    private Drone getCurrentDrone(String missionId) {
        return getCurrentDevice(missionId);
    }

    private String getCurrentOperatorId(String missionId) {
        return missionOperatorAssignmentRepository.findByMissionIdAndIsCurrentTrue(missionId)
                .map(MissionOperatorAssignment::getOperatorId)
                .orElse(null);
    }
}
