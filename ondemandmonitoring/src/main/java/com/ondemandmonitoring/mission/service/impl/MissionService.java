package com.ondemandmonitoring.mission.service.impl;

import com.ondemandmonitoring.common.exception.ApiException;
import com.ondemandmonitoring.common.api.PageResponse;
import com.ondemandmonitoring.common.exception.ErrorCode;
import com.ondemandmonitoring.device.domain.Device;
import com.ondemandmonitoring.device.enums.DeviceStatus;
import com.ondemandmonitoring.device.repository.DeviceRepository;
import com.ondemandmonitoring.device.domain.Drone;
import com.ondemandmonitoring.device.domain.DeviceTelemetry;
import com.ondemandmonitoring.device.domain.PreflightCheck;
import com.ondemandmonitoring.device.dto.response.PreflightCheckResponse;
import com.ondemandmonitoring.device.enums.DeviceOperationalStatus;
import com.ondemandmonitoring.device.enums.PreflightCheckStatus;
import com.ondemandmonitoring.device.repository.DroneRepository;
import com.ondemandmonitoring.device.repository.DeviceTelemetryRepository;
import com.ondemandmonitoring.device.repository.PersistedPreflightCheckRepository;
import com.ondemandmonitoring.device.service.DeviceTelemetryFreshness;
import com.ondemandmonitoring.mission.domain.ControlHandover;
import com.ondemandmonitoring.mission.domain.DeviceConnection;
import com.ondemandmonitoring.mission.domain.FlightToken;
import com.ondemandmonitoring.mission.domain.Mission;
import com.ondemandmonitoring.mission.domain.MissionOperatorAssignment;
import com.ondemandmonitoring.mission.domain.MissionPlan;
import com.ondemandmonitoring.device.domain.PostflightCheck;
import com.ondemandmonitoring.device.domain.PostflightCheckItem;
import com.ondemandmonitoring.mission.dto.response.FlightTokenResponse;
import com.ondemandmonitoring.mission.dto.response.MissionPlanResponse;
import com.ondemandmonitoring.mission.dto.response.MissionResponse;
import com.ondemandmonitoring.mission.dto.response.MissionTelemetryReadinessResponse;
import com.ondemandmonitoring.mission.dto.response.PostflightCheckResponse;
import com.ondemandmonitoring.mission.dto.request.PostFlightStatusRequest;
import com.ondemandmonitoring.mission.enums.CheckupStatus;
import com.ondemandmonitoring.mission.enums.DeviceRole;
import com.ondemandmonitoring.mission.enums.FeasibilityStatus;
import com.ondemandmonitoring.mission.enums.MissionStatus;
import com.ondemandmonitoring.mission.enums.InspectionResult;
import com.ondemandmonitoring.mission.repository.ControlHandoverRepository;
import com.ondemandmonitoring.mission.repository.DeviceConnectionRepository;
import com.ondemandmonitoring.mission.repository.FlightTokenRepository;
import com.ondemandmonitoring.mission.repository.MissionRepository;
import com.ondemandmonitoring.mission.mapper.FlightTokenMapper;
import com.ondemandmonitoring.mission.mapper.MissionMapper;
import com.ondemandmonitoring.mission.mapper.PostflightCheckMapper;
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
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.ondemandmonitoring.device.domain.MaintenanceTicket;
import com.ondemandmonitoring.device.repository.MaintenanceTicketRepository;
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
    DeviceRepository deviceRepository;
    DroneRepository droneRepository;
    DeviceTelemetryRepository DeviceTelemetryRepository;
    PersistedPreflightCheckRepository persistedPreflightCheckRepository;
    FlightTokenRepository flightTokenRepository;
    MissionMapper missionMapper;
    FlightTokenMapper flightTokenMapper;
    PostflightCheckMapper postflightCheckMapper;

    // Supporting audit & work order repositories
    MissionDeviceAssignmentRepository missionDeviceAssignmentRepository;
    MissionOperatorAssignmentRepository missionOperatorAssignmentRepository;
    ResourceTimeLockRepository resourceTimeLockRepository;
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

        Device device = findDeviceByIdOrSerial(droneId);
        if (device == null) {
            device = droneRepository.findById(droneId)
                    .map(Drone::getDevice)
                    .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Device not found"));
        }
        assignDeviceToMission(mission, device, DeviceRole.MAIN, "MANUAL_MANAGER");
        log.info("Mission {} assigned to device {}", missionId, device.getSerialNumber());
        Mission saved = missionRepository.save(mission);
        return missionMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public MissionResponse assignOperator(String missionId, String operatorId) {
        Mission mission = getOrThrow(missionId);
        requireStatus(mission, MissionStatus.RESOURCE_ASSIGNING);

        if (getCurrentAssignedDevice(missionId) == null) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "Must assign a device before assigning an operator.");
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
        Device assignedDevice = getCurrentAssignedDevice(missionId);
        if (assignedDevice == null) {
            throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Mission has no assigned device");
        }

        String deviceCode = assignedDevice.getSerialNumber();
        DeviceTelemetry telemetry = DeviceTelemetryRepository.readByDroneCode(deviceCode).orElse(null);
        Instant updatedAt = telemetry != null ? telemetry.getUpdatedAt() : null;
        boolean ready = telemetry != null
                && Boolean.TRUE.equals(telemetry.getConnected())
                && DeviceTelemetryFreshness.isFresh(updatedAt);
        return new MissionTelemetryReadinessResponse(deviceCode, ready, updatedAt);
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
        Device assignedDevice = getCurrentAssignedDevice(missionId);
        if (assignedDevice == null || !droneCode.equals(assignedDevice.getSerialNumber())) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "Preflight device does not match the mission assignment.");
        }
        DeviceConnection activeConnection = deviceConnectionRepository
                .findTopByMissionIdAndConnectionStatusOrderByConnectedAtDesc(missionId, "CONNECTED")
                .orElseThrow(() -> new ApiException(
                        ErrorCode.INVALID_REQUEST,
                        "Connect GCS and bind the mission before preflight."));
        if (activeConnection.getDevice() == null
                || !droneCode.equals(activeConnection.getDevice().getSerialNumber())) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "Connected device does not match the mission assignment.");
        }

        DeviceTelemetry telemetry = DeviceTelemetryRepository.findByDroneCode(droneCode).orElse(null);
        PreflightCheck runtimePass = recentPersistedPreflightPass(missionId)
                .orElseThrow(() -> new ApiException(
                        ErrorCode.INVALID_REQUEST,
                        "A completed runtime preflight PASS is required before backend confirmation."));
        if (runtimePass.getDeviceConnection() == null
                || !activeConnection.getId().equals(runtimePass.getDeviceConnection().getId())) {
            throw new ApiException(ErrorCode.INVALID_REQUEST,
                    "Runtime preflight result does not belong to the active device connection.");
        }

        MissionPlan plan = missionPlanningService.generateAStarEnergyAwarePlan(missionId);
        requireFeasiblePlan(plan, true);

        mission.setPreflightPassed(true);
        mission.setPreflightFaultType(null);
        mission.setPreflightFailureReason(null);
        mission.setPreflightCheckedAt(Instant.now());
        mission.setPreflightRetryCount(
                (mission.getPreflightRetryCount() == null ? 0 : mission.getPreflightRetryCount()) + 1
        );

        mission.setStatus(MissionStatus.READY_TO_FLY);
        device.setStatus(DeviceOperationalStatus.PREFLIGHT);
        droneRepository.save(device);

        FlightToken token = issueFlightToken(missionId, droneCode, getCurrentOperatorId(missionId));
        FlightTokenResponse tokenResponse = flightTokenMapper.toResponse(token);
        log.info("Mission {} persisted preflight PASSED – issued FlightToken {}", missionId, token.getTokenValue());

        missionRepository.save(mission);
        return preflightResponseFromRun(runtimePass, droneCode, missionId, telemetry, tokenResponse);
    }

    private java.util.Optional<PreflightCheck> recentPersistedPreflightPass(String missionId) {
        return persistedPreflightCheckRepository.findFirstByMissionIdOrderByCreatedAtDesc(missionId)
                .filter(run -> run.getStatus() == PreflightCheckStatus.PASSED)
                .filter(run -> run.getCompletedAt() == null
                        || Duration.between(run.getCompletedAt(), Instant.now()).abs().toMinutes() <= 10);
    }

    private PreflightCheckResponse preflightResponseFromRun(
            PreflightCheck run,
            String droneCode,
            String missionId,
            DeviceTelemetry telemetry,
            FlightTokenResponse tokenResponse) {
        return PreflightCheckResponse.builder()
                .id(run.getId())
                .droneCode(droneCode)
                .missionId(missionId)
                .overallPassed(true)
                .failureReason(null)
                .faultType(null)
                .batteryPercent(telemetry == null ? null : telemetry.getBatteryPercent())
                .gpsFixType(telemetry == null ? null : telemetry.getGpsFixType())
                .gpsSatelliteCount(telemetry == null ? null : telemetry.getGpsSatelliteCount())
                .gyrometerOk(true)
                .accelerometerOk(true)
                .magnetometerOk(true)
                .localPositionOk(true)
                .globalPositionOk(true)
                .homePositionOk(true)
                .armable(true)
                .connected(true)
                .inAir(false)
                .flightMode(telemetry == null ? null : telemetry.getFlightMode())
                .cameraOk(true)
                .gimbalOk(true)
                .storageOk(true)
                .weatherOk(true)
                .flightToken(tokenResponse)
                .checkedAt(run.getCompletedAt() == null ? run.getStartedAt() : run.getCompletedAt())
                .build();
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
        if (mission.getScheduledEndAt() == null && mission.getOrder().getPreferredTime() != null) {
            mission.setScheduledEndAt(date.atTime(mission.getOrder().getPreferredTime().getEndTime())
                    .atZone(ZoneId.of("Asia/Ho_Chi_Minh"))
                    .toInstant());
        }
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
        if (getCurrentAssignedDevice(mission.getId()) == null || missionPlanRepository.findByMissionId(mission.getId()).isPresent()) {
            return;
        }

        missionPlanningService.generateAStarEnergyAwarePlan(mission.getId());
    }

    @Override
    @Transactional
    public MissionResponse replaceDrone(String missionId, String newDroneCode) {
        Mission mission = getOrThrow(missionId);
        Device replacementDevice = findDeviceByIdOrSerial(newDroneCode);
        if (replacementDevice == null) {
            replacementDevice = droneRepository.findByDroneCode(newDroneCode)
                    .map(Drone::getDevice)
                    .orElseThrow(() -> new ApiException(ErrorCode.DEVICE_NOT_AVAILABLE, "Device " + newDroneCode + " không tồn tại"));
        }
        if (replacementDevice.getStatus() != DeviceStatus.AVAILABLE) {
            throw new ApiException(ErrorCode.DEVICE_NOT_AVAILABLE,
                    "Device " + newDroneCode + " is not AVAILABLE (status: " + replacementDevice.getStatus() + ")");
        }

        Device oldAssignedDevice = getCurrentAssignedDevice(missionId);
        if (oldAssignedDevice != null) {
            oldAssignedDevice.setStatus(DeviceStatus.MAINTENANCE);
            oldAssignedDevice.setOperationalStatus(DeviceOperationalStatus.MAINTENANCE);
            deviceRepository.save(oldAssignedDevice);
        }

        replacementDevice.setStatus(DeviceStatus.IN_USE);
        replacementDevice.setOperationalStatus(DeviceOperationalStatus.PREFLIGHT);
        deviceRepository.save(replacementDevice);

        MissionDeviceAssignment assignment = MissionDeviceAssignment.builder()
                .mission(mission)
                .device(replacementDevice)
                .deviceRole(DeviceRole.MAIN)
                .checkupStatus(CheckupStatus.PENDING)
                .postcheckStatus("MANUAL_SWAP")
                .failureNotes("Replacement after preflight failure")
                .build();
        missionDeviceAssignmentRepository.save(assignment);

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
        Device handoverDevice = getCurrentAssignedDevice(missionId);
        if (handoverDevice == null) {
            throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Mission " + missionId + " has no assigned device");
        }
        handover.setDevice(handoverDevice);
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
        mission.setActualStartAt(mission.getStartedAt());
        updateDeviceStatus(mission, DeviceOperationalStatus.ACTIVE_MISSION);

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
        updateDeviceStatus(mission, DeviceOperationalStatus.RETURNING);
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
        mission.setActualEndAt(mission.getCompletedAt());
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
            updateDeviceStatus(mission, DeviceOperationalStatus.MAINTENANCE);
        }
        mission.setStatus(MissionStatus.FAILED);
        mission.setActualEndAt(Instant.now());
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
    public MissionResponse updatePostFlightStatus(String missionId, DeviceOperationalStatus newDeviceStatus, String notes) {
        return savePostFlightStatus(missionId, newDeviceStatus, notes, Map.of());
    }

    @Override
    @Transactional
    public MissionResponse recordPostFlightInspection(String missionId, DeviceOperationalStatus newDeviceStatus,
                                                      String notes, Map<String, InspectionResult> results,
                                                      PostFlightStatusRequest.TelemetrySnapshot telemetrySnapshot) {
        Set<String> required = Set.of("a1", "a2", "p1", "p2", "e1", "e2", "e3", "e4", "d1");
        if (results == null || !results.keySet().equals(required)
                || results.values().stream().anyMatch(value -> value == null)) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "All nine inspection items must have a result");
        }
        boolean faultDetected = results.containsValue(InspectionResult.FAIL);
        DeviceOperationalStatus expected = faultDetected ? DeviceOperationalStatus.MAINTENANCE : DeviceOperationalStatus.AVAILABLE;
        if (newDeviceStatus != expected) {
            throw new ApiException(ErrorCode.INVALID_REQUEST,
                    "Device status does not match the submitted inspection results");
        }
        Mission mission = getOrThrow(missionId);
        requireStatus(mission, MissionStatus.POSTFLIGHT_CHECKING);
        String summary = results.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(java.util.stream.Collectors.joining(", "));
        String auditNotes = "Inspection: " + summary + ". " + (notes == null ? "" : notes.strip());
        return savePostFlightStatus(missionId, newDeviceStatus, auditNotes, results, telemetrySnapshot);
    }

    private MissionResponse savePostFlightStatus(String missionId, DeviceOperationalStatus newDeviceStatus,
                                                 String notes, Map<String, InspectionResult> results) {
        return savePostFlightStatus(missionId, newDeviceStatus, notes, results, null);
    }

    private MissionResponse savePostFlightStatus(String missionId, DeviceOperationalStatus newDeviceStatus,
                                                 String notes, Map<String, InspectionResult> results,
                                                 PostFlightStatusRequest.TelemetrySnapshot telemetrySnapshot) {
        Mission mission = getOrThrow(missionId);
        Device device = getCurrentAssignedDevice(mission.getId());
        if (device == null) {
            throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Mission " + missionId + " has no assigned device");
        }
        device.setStatus(toDeviceStatus(newDeviceStatus));
        device.setOperationalStatus(newDeviceStatus);
        deviceRepository.save(device);

        // Record PostflightCheck physical inspection
        boolean overallOk = (newDeviceStatus != DeviceOperationalStatus.MAINTENANCE);
        PostflightCheck postflightCheck = new PostflightCheck();
        postflightCheck.setMission(mission);
        DeviceConnection activeConnection = deviceConnectionRepository
                .findTopByMissionIdAndConnectionStatusOrderByConnectedAtDesc(missionId, "CONNECTED")
                .orElseThrow(() -> new ApiException(ErrorCode.INVALID_REQUEST,
                        "Postflight check requires an active device connection for mission " + missionId));
        if (activeConnection.getDevice() == null
                || !device.getId().equals(activeConnection.getDevice().getId())) {
            throw new ApiException(ErrorCode.INVALID_REQUEST,
                    "Active device connection does not match the assigned mission device.");
        }
        postflightCheck.setDeviceConnection(activeConnection);
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
        addPostflightItems(postflightCheck, results);
        postflightCheckRepository.save(postflightCheck);

        // Auto-create MaintenanceTicket if device requires maintenance post-flight
        if (newDeviceStatus == DeviceOperationalStatus.MAINTENANCE) {
            MaintenanceTicket ticket = new MaintenanceTicket();
            ticket.setTicketCode("TKT-POSTFLIGHT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
            ticket.setDevice(device); // device field — device-type agnostic
            ticket.setReportedBy(getCurrentOperatorId(missionId));
            ticket.setIssueType("POSTFLIGHT_DAMAGE");
            ticket.setSeverity("HIGH");
            ticket.setDescription("Post-flight physical inspection flagged maintenance needed for device " + device.getSerialNumber() + ". Notes: " + notes);
            ticket.setStatus("OPEN");
            ticket.setOpenedAt(Instant.now());
            maintenanceTicketRepository.save(ticket);
            log.error("[MAINTENANCE] Auto-created MaintenanceTicket {} for device {}", ticket.getTicketCode(), device.getSerialNumber());
        }

        if (mission.getStatus() == MissionStatus.POSTFLIGHT_CHECKING) {
            mission.setStatus(MissionStatus.COMPLETED);
            mission.setCompletedAt(Instant.now());
            mission.setActualEndAt(mission.getCompletedAt());
            releaseMissionResources(mission, "MISSION_COMPLETE");
        }

        if (notes != null && !notes.isBlank()) {
            log.info("Mission {} post-flight notes: {}", missionId, notes);
        }
        log.info("Mission {} post-flight completed – device {} status set to {}", missionId, device.getSerialNumber(), newDeviceStatus);
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

    private void addPostflightItems(PostflightCheck postflightCheck, Map<String, InspectionResult> results) {
        if (results == null || results.isEmpty()) {
            return;
        }
        Instant checkedAt = postflightCheck.getCheckedAt() == null ? Instant.now() : postflightCheck.getCheckedAt();
        for (PostflightItemDefinition definition : POSTFLIGHT_ITEM_DEFINITIONS) {
            InspectionResult status = results.get(definition.type());
            if (status == null) {
                continue;
            }
            PostflightCheckItem item = new PostflightCheckItem();
            item.setPostflightCheck(postflightCheck);
            item.setCheckType(definition.type());
            item.setCheckName(definition.name());
            item.setStatus(status);
            item.setMessage(definition.message());
            item.setCheckedAt(checkedAt);
            postflightCheck.getItems().add(item);
        }
    }

    private static final List<PostflightItemDefinition> POSTFLIGHT_ITEM_DEFINITIONS = List.of(
            new PostflightItemDefinition("a1", "Cánh quạt và chân đáp", "Không nứt gãy, không cong vênh sau khi hạ cánh"),
            new PostflightItemDefinition("a2", "Khung thân và gimbal", "Khung thân, gimbal camera không có dấu hiệu va chạm"),
            new PostflightItemDefinition("p1", "Động cơ", "Động cơ quay ổn định, không kẹt vật thể"),
            new PostflightItemDefinition("p2", "ESC và nhiệt động cơ", "Không quá nhiệt, không báo lỗi điều tốc"),
            new PostflightItemDefinition("e1", "Pin", "Pin không phồng rộp, mức pin sau bay hợp lệ"),
            new PostflightItemDefinition("e2", "Camera và cảm biến", "Ống kính sạch, ghi hình/truyền ảnh ổn định"),
            new PostflightItemDefinition("e3", "GPS / RTK", "Định vị ổn định, không mất tọa độ bất thường"),
            new PostflightItemDefinition("e4", "Tiếp điểm điện", "Tiếp điểm sạch, không lỏng hoặc cháy xém"),
            new PostflightItemDefinition("d1", "Telemetry và video link", "Kết nối GCS ổn định sau khi hạ cánh"));

    private record PostflightItemDefinition(String type, String name, String message) {}

    @Override
    @Transactional(readOnly = true)
    public PostflightCheckResponse getLatestPostflightCheck(String missionId) {
        return postflightCheckRepository.findTopByMissionIdOrderByCheckedAtDesc(missionId)
                .map(postflightCheckMapper::toResponse)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND,
                        "No postflight check found for mission " + missionId));
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

    private void updateDeviceStatus(Mission mission, DeviceOperationalStatus newStatus) {
        Device assignedDevice = getCurrentAssignedDevice(mission.getId());
        if (assignedDevice != null) {
            assignedDevice.setStatus(toDeviceStatus(newStatus));
            assignedDevice.setOperationalStatus(newStatus);
            deviceRepository.save(assignedDevice);
        }
    }

    private void releaseMissionResources(Mission mission, String reason) {
        Device assignedDevice = getCurrentAssignedDevice(mission.getId());
        if (assignedDevice != null && assignedDevice.getStatus() != DeviceStatus.MAINTENANCE) {
            assignedDevice.setStatus(DeviceStatus.AVAILABLE);
            assignedDevice.setOperationalStatus(DeviceOperationalStatus.AVAILABLE);
            deviceRepository.save(assignedDevice);
        }

        missionDeviceAssignmentRepository.findFirstByMissionIdAndDeviceRoleOrderByCreatedAtDesc(mission.getId(), DeviceRole.MAIN)
                .or(() -> missionDeviceAssignmentRepository.findFirstByMissionIdOrderByCreatedAtDesc(mission.getId()))
                .ifPresent(assignment -> {
                    assignment.setPostcheckStatus(reason);
                    missionDeviceAssignmentRepository.save(assignment);
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

    private void assignDeviceToMission(Mission mission, Device device, DeviceRole role, String assignmentSource) {
        if (device.getStatus() == DeviceStatus.MAINTENANCE) {
            throw new ApiException(ErrorCode.DEVICE_NOT_AVAILABLE,
                    "Device [" + device.getSerialNumber() + "] is under MAINTENANCE and cannot be assigned to a mission.");
        }
        if (device.getStatus() != DeviceStatus.AVAILABLE) {
            throw new ApiException(ErrorCode.DEVICE_NOT_AVAILABLE,
                    "Device [" + device.getSerialNumber() + "] is not AVAILABLE (current status: " + device.getStatus() + ")");
        }

        Device oldDevice = getCurrentAssignedDevice(mission.getId());
        if (oldDevice != null) {
            oldDevice.setStatus(DeviceStatus.AVAILABLE);
            deviceRepository.save(oldDevice);
        }

        lockDeviceTime(mission, device);

        device.setStatus(DeviceStatus.IN_USE);
        deviceRepository.save(device);

        MissionDeviceAssignment assignment = MissionDeviceAssignment.builder()
                .mission(mission)
                .device(device)
                .deviceRole(role != null ? role : DeviceRole.MAIN)
                .checkupStatus(CheckupStatus.PENDING)
                .postcheckStatus(assignmentSource)
                .build();
        missionDeviceAssignmentRepository.save(assignment);
    }

    private void lockDeviceTime(Mission mission, Device device) {
        ensureScheduledStart(mission);
        if (mission.getScheduledStartAt() == null || mission.getScheduledEndAt() == null) {
            return;
        }

        String resourceId = device.getId();
        if (resourceTimeLockRepository.findByResourceIdAndMissionId(resourceId, mission.getId()).isPresent()) {
            throw new ApiException(ErrorCode.RESOURCE_ALREADY_EXISTS,
                    "Device is already assigned to this mission.");
        }

        Instant paddedStart = mission.getScheduledStartAt().minus(1, ChronoUnit.HOURS);
        Instant paddedEnd = mission.getScheduledEndAt().plus(1, ChronoUnit.HOURS);
        for (ResourceTimeLock lock : resourceTimeLockRepository.findByResourceId(resourceId)) {
            if (lock.getMission() != null && lock.getMission().getId().equals(mission.getId())) {
                continue;
            }
            if (lock.getStartTime() != null && lock.getEndTime() != null
                    && lock.getStartTime().isBefore(paddedEnd)
                    && lock.getEndTime().isAfter(paddedStart)) {
                throw new ApiException(ErrorCode.SCHEDULE_CONFLICT,
                        "Device schedule conflicts with an existing resource lock (1-hour buffer required)");
            }
        }

        resourceTimeLockRepository.save(ResourceTimeLock.builder()
                .resourceId(resourceId)
                .mission(mission)
                .startTime(mission.getScheduledStartAt())
                .endTime(mission.getScheduledEndAt())
                .lockStatus(com.ondemandmonitoring.mission.enums.LockStatus.HARD_LOCK)
                .expiresAt(null)
                .build());
    }

    private Device getCurrentAssignedDevice(String missionId) {
        return missionDeviceAssignmentRepository
                .findFirstByMissionIdAndDeviceRoleOrderByCreatedAtDesc(missionId, DeviceRole.MAIN)
                .or(() -> missionDeviceAssignmentRepository.findFirstByMissionIdOrderByCreatedAtDesc(missionId))
                .map(MissionDeviceAssignment::getDevice)
                .orElse(null);
    }

    private Device findDeviceByIdOrSerial(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return deviceRepository.findById(value)
                .or(() -> deviceRepository.findBySerialNumber(value))
                .orElse(null);
    }

    private DeviceStatus toDeviceStatus(DeviceOperationalStatus status) {
        if (status == DeviceOperationalStatus.MAINTENANCE) {
            return DeviceStatus.MAINTENANCE;
        }
        if (status == DeviceOperationalStatus.AVAILABLE) {
            return DeviceStatus.AVAILABLE;
        }
        return DeviceStatus.IN_USE;
    }

    private String getCurrentOperatorId(String missionId) {
        return missionOperatorAssignmentRepository.findByMissionIdAndIsCurrentTrue(missionId)
                .map(MissionOperatorAssignment::getOperatorId)
                .orElse(null);
    }
}
