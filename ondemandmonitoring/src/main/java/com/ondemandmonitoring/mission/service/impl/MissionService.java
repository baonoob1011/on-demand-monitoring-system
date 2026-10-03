package com.ondemandmonitoring.mission.service.impl;

import com.ondemandmonitoring.common.exception.ApiException;
import com.ondemandmonitoring.common.api.PageResponse;
import com.ondemandmonitoring.common.exception.ErrorCode;
import com.ondemandmonitoring.device.domain.Device;
import com.ondemandmonitoring.device.enums.DeviceStatus;
import com.ondemandmonitoring.device.repository.DeviceRepository;
import com.ondemandmonitoring.devicecheck.dto.response.PreDeviceCheckResponse;
import com.ondemandmonitoring.devicecheck.repository.PersistedPostDeviceCheckRepository;
import com.ondemandmonitoring.devicecheck.service.IPreDeviceCheckCompletionService;
import com.ondemandmonitoring.devicecheck.service.IPersistedPostDeviceCheckService;
import com.ondemandmonitoring.mission.domain.ControlHandover;
import com.ondemandmonitoring.mission.domain.DeviceConnection;
import com.ondemandmonitoring.mission.domain.FlightToken;
import com.ondemandmonitoring.mission.domain.Mission;
import com.ondemandmonitoring.mission.domain.MissionPlan;
import com.ondemandmonitoring.mission.dto.request.*;
import com.ondemandmonitoring.mission.dto.response.FlightTokenResponse;
import com.ondemandmonitoring.mission.dto.response.MissionPlanResponse;
import com.ondemandmonitoring.mission.dto.response.MissionResponse;
import com.ondemandmonitoring.mission.dto.response.MissionTelemetryReadinessResponse;
import com.ondemandmonitoring.mission.enums.*;
import com.ondemandmonitoring.order.domain.Order;
import com.ondemandmonitoring.order.enums.OrderStatus;
import com.ondemandmonitoring.mission.repository.ControlHandoverRepository;
import com.ondemandmonitoring.mission.repository.DeviceConnectionRepository;
import com.ondemandmonitoring.mission.repository.FlightTokenRepository;
import com.ondemandmonitoring.mission.repository.MissionRepository;
import com.ondemandmonitoring.mission.mapper.FlightTokenMapper;
import com.ondemandmonitoring.mission.mapper.MissionMapper;
import com.ondemandmonitoring.mission.service.IDeviceConnectionService;
import com.ondemandmonitoring.mission.service.IFlightTokenService;
import com.ondemandmonitoring.mission.service.IMissionResultService;
import com.ondemandmonitoring.mission.service.IMissionService;
import com.ondemandmonitoring.order.repository.OrderRepository;
import com.ondemandmonitoring.planning.service.MissionPlanningService;
import com.ondemandmonitoring.role.domain.RoleCode;
import com.ondemandmonitoring.user.domain.User;
import com.ondemandmonitoring.user.service.IStaffDirectoryService;
import com.ondemandmonitoring.user.service.AuthenticatedUserResolver;
import com.ondemandmonitoring.userschedule.domain.UserSchedule;
import com.ondemandmonitoring.userschedule.enums.UserScheduleStatus;
import com.ondemandmonitoring.userschedule.enums.UserScheduleType;
import com.ondemandmonitoring.userschedule.repository.UserScheduleRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import com.ondemandmonitoring.device.domain.MaintenanceTicket;
import com.ondemandmonitoring.device.repository.MaintenanceTicketRepository;
import com.ondemandmonitoring.mission.domain.*;
import com.ondemandmonitoring.mission.repository.*;

/**
 * Implementation of {@link IMissionService} for mission lifecycle management.
 * Enterprise pattern: Maps entities to DTOs within @Transactional scope to
 * guarantee safety against LazyInitializationException.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class MissionService implements IMissionService {

    static final long TOKEN_TTL_SECONDS = 900L; // 15 minutes
    // Only the pilot is mandatory; missing OPERATOR/MAINTAINER/INSPECTOR roles make the matching flow step skipped.
    static final Set<MissionStaffRole> REQUIRED_CREW_ROLES = Set.of(MissionStaffRole.PILOT);

    MissionRescheduleHistoryRepository missionRescheduleHistoryRepository;
    MissionRepository missionRepository;
    DeviceRepository deviceRepository;
    FlightTokenRepository flightTokenRepository;
    MissionMapper missionMapper;
    FlightTokenMapper flightTokenMapper;

    // Supporting audit & work order repositories
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
    // =========================================================================
    // Query Methods
    // =========================================================================

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("@missionAuthorizationService.canManageMissions()")
    public PageResponse<MissionResponse> searchStaffMissions(
            MissionStatus status, Instant from, Instant toExclusive, Pageable pageable) {
        Specification<Mission> criteria = (root, query, builder) -> {
            var predicates = new ArrayList<jakarta.persistence.criteria.Predicate>();
            if (status != null)
                predicates.add(builder.equal(root.get("status"), status));
            if (from != null)
                predicates.add(builder.greaterThanOrEqualTo(root.get("scheduledStartAt"), from));
            if (toExclusive != null)
                predicates.add(builder.lessThan(root.get("scheduledStartAt"), toExclusive));
            return builder.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
        return PageResponse.from(missionRepository.findAll(criteria, pageable).map(missionMapper::toResponse));
    }

    @Override
    @Transactional
    @PreAuthorize("@missionAuthorizationService.canViewMission(#missionId)")
    public MissionResponse getByIdResponse(String missionId) {
        Mission mission = getOrThrow(missionId);
        ensureAcceptedMissionPlan(mission);
        return missionMapper.toResponse(mission);
    }
        @Override
    @Transactional(readOnly = true)
    @PreAuthorize("@missionAuthorizationService.canManageMissions()")
    public List<MissionResponse> getAllMissions() {
        return missionRepository.findAll().stream()
                .map(missionMapper::toResponse)
                .toList();
    }
    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("@missionAuthorizationService.canViewMission(#missionCode)")
    public MissionResponse getByCodeResponse(String missionCode) {
        Mission mission = missionRepository.findByMissionCode(missionCode)
                .orElseThrow(() -> new ApiException(ErrorCode.MISSION_NOT_FOUND,
                        "Mission not found: " + missionCode));
        return missionMapper.toResponse(mission);
    }

    @Override
    @Transactional
    @PreAuthorize("@missionAuthorizationService.canManageMissions()")
    public MissionResponse createMission(MissionCreateRequest request) {
        var existingMission = missionRepository.findByOrderId(request.getOrderId());
        if (existingMission.isPresent()) {
            return missionMapper.toResponse(existingMission.get());
        }

        Order order = orderRepository.findById(request.getOrderId())
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND,
                        "Order not found with id: " + request.getOrderId()));
        if (order.getOrderStatus() != OrderStatus.APPROVED) {
            throw new ApiException(ErrorCode.INVALID_REQUEST,
                    "Order status must be APPROVED to create a mission");
        }

        validateScheduledDates(request.getScheduledStartAt(), request.getScheduledEndAt(), order);

        Mission mission = new Mission();
        mission.setMissionCode(generateMissionCode());
        mission.setStatus(MissionStatus.RESOURCE_ASSIGNING);
        mission.setOrder(order);
        mission.setScheduledStartAt(request.getScheduledStartAt());
        mission.setScheduledEndAt(request.getScheduledEndAt());

        Mission saved = missionRepository.save(mission);
        missionPlanningService.generateAStarEnergyAwarePlan(saved.getId());
        return missionMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("@missionAuthorizationService.canViewStaffMissions(#staffId)")
    public List<MissionResponse> getByStaffId(String staffId) {
        List<MissionStatus> activeStatuses = List.of(MissionStatus.values());
        return missionRepository.findByStaffIdAndStatusIn(staffId, activeStatuses)
                .stream()
                .sorted(java.util.Comparator.comparing(
                        Mission::getScheduledStartAt,
                        java.util.Comparator.nullsLast(java.util.Comparator.reverseOrder())))
                .map(missionMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('STAFF')")
    public List<MissionResponse> getCurrentStaffMissions() {
        return getByStaffId(currentStaffId());
    }



    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("@missionAuthorizationService.canManageMissions()")
    public List<MissionResponse> getPendingAssignmentMissions() {
        return missionRepository.findByStatusIn(List.of(
                MissionStatus.CREATED,
                MissionStatus.RESOURCE_ASSIGNING))
                .stream()
                .sorted(java.util.Comparator.comparing(
                        Mission::getCreatedAt,
                        java.util.Comparator.nullsLast(java.util.Comparator.reverseOrder())))
                .map(missionMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("@missionAuthorizationService.canViewMission(#missionId)")
    public MissionPlanResponse getMissionPlan(String missionId) {
        Mission mission = getOrThrow(missionId);
        MissionPlan plan = missionPlanRepository.findByMissionId(mission.getId())
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
    @PreAuthorize("@missionAuthorizationService.canRespondToMission(#missionId)")
    public MissionResponse acceptCurrentStaffMission(String missionId) {
        return acceptMission(missionId, currentStaffId());
    }

    @Override
    @Transactional
    @PreAuthorize("@missionAuthorizationService.canRespondToMission(#missionId)")
    public MissionResponse rejectCurrentStaffMission(String missionId, String reason) {
        return rejectMission(missionId, currentStaffId(), reason);
    }

    @Override
    @Transactional
    @PreAuthorize("@missionAuthorizationService.canControlFlight(#missionId)")
    public MissionResponse handoverCurrentStaffControl(String missionId) {
        return handoverControl(missionId, currentStaffId());
    }



    @Override
    @Transactional
    @PreAuthorize("@missionAuthorizationService.canManageMissions()")
    public MissionResponse assignDevice(String missionId, AssignDeviceRequest request) {
        String deviceId = request.getDeviceId();
        DeviceRole deviceRole = request.getDeviceRole();
        Mission mission = getOrThrow(missionId);
        String resolvedMissionId = mission.getId();
        if (mission.getStatus() != MissionStatus.RESOURCE_ASSIGNING
                && mission.getStatus() != MissionStatus.WAITING_CREW_CONFIRMATION) {
            throw new ApiException(ErrorCode.INVALID_REQUEST,
                    "Mission status must be RESOURCE_ASSIGNING to assign a device.");
        }

        Device device = deviceRepository.findById(deviceId)
                .orElseThrow(
                        () -> new ApiException(ErrorCode.DEVICE_NOT_FOUND, "Device not found with id: " + deviceId));

        if (device.getStatus() == DeviceStatus.MAINTENANCE) {
            throw new ApiException(ErrorCode.DEVICE_NOT_AVAILABLE,
                    "Device is under MAINTENANCE and cannot be assigned to a mission.");
        }

        Instant missionStart = mission.getScheduledStartAt();
        Instant missionEnd = mission.getScheduledEndAt();

        if (missionStart == null || missionEnd == null) {
            throw new ApiException(ErrorCode.INVALID_REQUEST,
                    "Mission schedule start and end times must be set before assigning devices.");
        }

        Instant paddedStart = missionStart.minus(1, ChronoUnit.HOURS);
        Instant paddedEnd = missionEnd.plus(1, ChronoUnit.HOURS);
        DeviceRole role = (deviceRole != null) ? deviceRole : DeviceRole.MAIN;

        List<ResourceTimeLock> existingLocks = resourceTimeLockRepository.findByResourceId(deviceId);
        boolean hasLockForThisMission = false;
        for (ResourceTimeLock lock : existingLocks) {
            if (isReleasedLock(lock)) {
                continue;
            }
            if (lock.getMission() != null && lock.getMission().getId().equals(resolvedMissionId)) {
                hasLockForThisMission = true;
                continue;
            }
            if (lock.getStartTime() != null && lock.getEndTime() != null) {
                if (lock.getStartTime().isBefore(paddedEnd) && lock.getEndTime().isAfter(paddedStart)) {
                    throw new ApiException(ErrorCode.SCHEDULE_CONFLICT,
                            "Device schedule conflicts with an existing resource lock (1-hour buffer required)");
                }
            }
        }

        if (!resourceTimeLockRepository.findAllByResourceIdAndMissionId(deviceId, resolvedMissionId).isEmpty()) {
            hasLockForThisMission = true;
        }

        Instant now = Instant.now();
        var currentSameDevice = missionDeviceAssignmentRepository
                .findAllByMissionIdAndDeviceIdAndIsCurrentTrueOrderByCreatedAtDesc(resolvedMissionId, deviceId);
        if (!currentSameDevice.isEmpty()) {
            if (hasRequiredCrewAssigned(resolvedMissionId)) {
                mission.setStatus(MissionStatus.WAITING_CREW_CONFIRMATION);
            }
            return missionMapper.toResponse(missionRepository.save(mission));
        }

        if (!hasLockForThisMission) {
            ResourceTimeLock lock = ResourceTimeLock.builder()
                    .resourceId(deviceId)
                    .mission(mission)
                    .startTime(missionStart)
                    .endTime(missionEnd)
                    .lockStatus(LockStatus.HARD_LOCK)
                    .expiresAt(null)
                    .build();
            resourceTimeLockRepository.save(lock);
        }

        if (role == DeviceRole.MAIN) {
            List<MissionDeviceAssignment> currentMainAssignments =
                    missionDeviceAssignmentRepository.findAllByMissionIdAndDeviceRoleAndIsCurrentTrueOrderByCreatedAtDesc(
                            resolvedMissionId,
                            DeviceRole.MAIN);
            for (MissionDeviceAssignment assignment : currentMainAssignments) {
                assignment.setIsCurrent(false);
                assignment.setReleasedAt(now);
                assignment.setReleaseReason("Replaced by device assignment " + deviceId);
            }
            if (!currentMainAssignments.isEmpty()) {
                missionDeviceAssignmentRepository.saveAll(currentMainAssignments);
            }
        }

        MissionDeviceAssignment newAssignment = MissionDeviceAssignment.builder()
                .mission(mission)
                .device(device)
                .deviceRole(role)
                .checkupStatus(CheckupStatus.PENDING)
                .postcheckStatus(null)
                .verifiedBy(null)
                .verifiedAt(null)
                .failureNotes(null)
                .assignedAt(now)
                .build();

        missionDeviceAssignmentRepository.save(newAssignment);
        if (hasRequiredCrewAssigned(resolvedMissionId)) {
            mission.setStatus(MissionStatus.WAITING_CREW_CONFIRMATION);
        }
        Mission saved = missionRepository.save(mission);
        return missionMapper.toResponse(saved);
    }


    @Override
    @Transactional
    @PreAuthorize("@missionAuthorizationService.canManageMissions()")
    public MissionResponse assignStaff(String missionId, AssignStaffRequest request) {
        String staffId = request.getStaffId();
        MissionStaffRole assignedRole = resolveStaffRole(request.getAssignedRole());

        Mission mission = getOrThrow(missionId);
        String resolvedMissionId = mission.getId();
        if (mission.getStatus() != MissionStatus.RESOURCE_ASSIGNING
                && mission.getStatus() != MissionStatus.WAITING_CREW_CONFIRMATION) {
            throw new ApiException(ErrorCode.INVALID_REQUEST,
                    "Mission status must be RESOURCE_ASSIGNING to assign staff.");
        }

        User staff = staffDirectory.requireActiveStaff(staffId);

        Instant missionStart = mission.getScheduledStartAt();
        Instant missionEnd = mission.getScheduledEndAt();

        if (missionStart == null || missionEnd == null) {
            throw new ApiException(ErrorCode.INVALID_REQUEST,
                    "Mission schedule start and end times must be set before assigning staff.");
        }

        Instant paddedStart = missionStart.minus(1, ChronoUnit.HOURS);
        Instant paddedEnd = missionEnd.plus(1, ChronoUnit.HOURS);

        if (!missionStaffAssignmentRepository
                .findAllByMissionIdAndStaffIdAndAssignedRoleAndIsCurrentTrueOrderByAssignedAtDesc(
                        resolvedMissionId,
                        staffId,
                        assignedRole)
                .isEmpty()) {
            if (missionDeviceAssignmentRepository.existsByMissionId(resolvedMissionId)
                    && hasRequiredCrewAssigned(resolvedMissionId)) {
                mission.setStatus(MissionStatus.WAITING_CREW_CONFIRMATION);
            }
            return missionMapper.toResponse(missionRepository.save(mission));
        }

        boolean staffAlreadyInMission = missionStaffAssignmentRepository
                .findAllByMissionIdAndStaffIdAndIsCurrentTrueOrderByAssignedAtDesc(resolvedMissionId, staffId)
                .isEmpty() == false;

        List<ResourceTimeLock> existingLocks = resourceTimeLockRepository.findByResourceId(staffId);
        if (!staffAlreadyInMission) {
            validateStaffSchedule(staffId, missionStart, missionEnd, paddedStart, paddedEnd);

            for (ResourceTimeLock lock : existingLocks) {
                if (isReleasedLock(lock)) {
                    continue;
                }
                if (lock.getMission() != null && lock.getMission().getId().equals(resolvedMissionId)) {
                    continue;
                }
                if (lock.getStartTime() != null && lock.getEndTime() != null) {
                    if (lock.getStartTime().isBefore(paddedEnd) && lock.getEndTime().isAfter(paddedStart)) {
                        throw new ApiException(ErrorCode.SCHEDULE_CONFLICT,
                                "Staff schedule conflicts with an existing resource lock (1-hour buffer required)");
                    }
                }
            }

            if (resourceTimeLockRepository.findAllByResourceIdAndMissionId(staffId, resolvedMissionId).isEmpty()) {
                ResourceTimeLock lock = ResourceTimeLock.builder()
                        .resourceId(staffId)
                        .mission(mission)
                        .startTime(missionStart)
                        .endTime(missionEnd)
                        .lockStatus(LockStatus.HARD_LOCK)
                        .expiresAt(missionStart.minus(1, ChronoUnit.HOURS))
                        .build();
                resourceTimeLockRepository.save(lock);
            }
        }

        MissionStaffAssignment assignment = MissionStaffAssignment.builder()
                .mission(mission)
                .staff(staff)
                .assignedRole(assignedRole)
                .responseStatus(StaffResponseStatus.PENDING)
                .assignedAt(Instant.now())
                .build();
        missionStaffAssignmentRepository.save(assignment);

        UserSchedule staffSchedule = UserSchedule.builder()
                .staff(staff)
                .startTime(missionStart)
                .endTime(missionEnd)
                .scheduleType(UserScheduleType.MISSION)
                .referenceId(mission.getId())
                .status(UserScheduleStatus.SCHEDULED)
                .notes("Assigned to mission " + mission.getMissionCode() + " as " + assignedRole)
                .build();
        userScheduleRepository.save(staffSchedule);

        if (missionDeviceAssignmentRepository.existsByMissionId(resolvedMissionId)
                && hasRequiredCrewAssigned(resolvedMissionId)) {
            mission.setStatus(MissionStatus.WAITING_CREW_CONFIRMATION);
        }
        Mission saved = missionRepository.save(mission);
        return missionMapper.toResponse(saved);
    }
    @Override
    @Transactional
    @PreAuthorize("@missionAuthorizationService.canManageMissions()")
    public MissionResponse updateMission(String missionId, MissionUpdateRequest request) {
        Mission mission = getOrThrow(missionId);

        Instant previousStart = mission.getScheduledStartAt();
        Instant previousEnd = mission.getScheduledEndAt();

        Instant newStart = request.getScheduledStartAt() != null ? request.getScheduledStartAt() : previousStart;
        Instant newEnd = request.getScheduledEndAt() != null ? request.getScheduledEndAt() : previousEnd;

        validateScheduledDates(newStart, newEnd, mission.getOrder());

        mission.setScheduledStartAt(newStart);
        mission.setScheduledEndAt(newEnd);

        Mission saved = missionRepository.save(mission);

        String rescheduledBy = authenticatedUserResolver.getCurrentUser().getId();

        MissionRescheduleHistory history = new MissionRescheduleHistory();
        history.setMission(saved);
        history.setPreviousStartTime(previousStart);
        history.setPreviousEndTime(previousEnd);
        history.setNewStartTime(saved.getScheduledStartAt());
        history.setNewEndTime(saved.getScheduledEndAt());
        history.setRescheduleReason(request.getRescheduleReason());
        history.setRescheduledBy(rescheduledBy);
        history.setRescheduledAt(Instant.now());
        missionRescheduleHistoryRepository.save(history);

        return missionMapper.toResponse(saved);
    }

    private MissionStaffRole resolveStaffRole(String role) {
        if (role == null || role.isBlank()) {
            return MissionStaffRole.PILOT;
        }
        try {
            return MissionStaffRole.valueOf(role.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "Invalid staff role: " + role);
        }
    }

    @Override
    @Transactional
    @PreAuthorize("@missionAuthorizationService.canRespondToMission(#missionId,#staffId)")
    public MissionResponse rejectMission(String missionId, String staffId, String reason) {
        Mission mission = getOrThrow(missionId);
        String resolvedMissionId = mission.getId();
        requireCrewConfirmationStatus(mission);
        List<MissionStaffAssignment> assignments =
                requireCurrentStaffAssignments(resolvedMissionId, staffId);

        Instant now = Instant.now();
        assignments.forEach(assignment -> {
            if (assignment.getResponseStatus() != StaffResponseStatus.PENDING) return;
            assignment.setResponseStatus(StaffResponseStatus.REJECTED);
            assignment.setDeclineReason(reason);
            assignment.setRespondedAt(now);
        });
        missionStaffAssignmentRepository.saveAll(assignments);

        mission.setStatus(MissionStatus.RESOURCE_ASSIGNING);
        Mission saved = missionRepository.save(mission);
        return missionMapper.toResponse(saved);
    }

    @Override
    @Transactional
    @PreAuthorize("@missionAuthorizationService.canRespondToMission(#missionId,#staffId)")
    public MissionResponse acceptMission(String missionId, String staffId) {
        Mission mission = getOrThrow(missionId);
        String resolvedMissionId = mission.getId();
        requireCrewConfirmationStatus(mission);
        List<MissionStaffAssignment> assignments =
                requireCurrentStaffAssignments(resolvedMissionId, staffId);

        Instant now = Instant.now();
        assignments.forEach(assignment -> {
            if (assignment.getResponseStatus() != StaffResponseStatus.PENDING) return;
            assignment.setResponseStatus(StaffResponseStatus.ACCEPTED);
            assignment.setRespondedAt(now);
        });
        missionStaffAssignmentRepository.saveAll(assignments);

        if (isRequiredCrewAccepted(resolvedMissionId)) {
            mission.setStatus(MissionStatus.SCHEDULED);
            ensureAcceptedMissionPlan(mission);
        }
        return missionMapper.toResponse(missionRepository.save(mission));
    }



    // =========================================================================
    // F3.2 – GCS Pairing & Pre-flight Gate
    // =========================================================================

    @Override
    @Transactional
    @PreAuthorize("@missionAuthorizationService.canControlFlight(#missionId)")
    public MissionResponse connectGcs(String missionId) {
        return deviceConnectionService.connectGcs(getOrThrow(missionId).getId());
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("@missionAuthorizationService.canViewMission(#missionId)")
    public MissionTelemetryReadinessResponse getTelemetryReadiness(String missionId) {
        Mission mission = getOrThrow(missionId);
        Device assignedDevice = getCurrentDevice(mission.getId());
        if (assignedDevice == null) {
            throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Mission has no assigned device");
        }

        return new MissionTelemetryReadinessResponse(assignedDevice.getDeviceCode(), false, null);
    }

    @Override
    @Transactional
    @PreAuthorize("@missionAuthorizationService.canControlFlight(#missionId)")
    public MissionResponse disconnectGcs(String missionId, String disconnectReason) {
        return deviceConnectionService.disconnectGcs(getOrThrow(missionId).getId(), disconnectReason);
    }

    @Override
    @Transactional
    public MissionResponse handleGcsSessionLost(String missionId, String reason) {
        return deviceConnectionService.handleGcsSessionLost(getOrThrow(missionId).getId(), reason);
    }

    @Override
    @Transactional
    @PreAuthorize("@missionAuthorizationService.canInspectDevice(#missionId)")
    public PreDeviceCheckResponse runPreDeviceCheck(String missionId, String deviceId) {
        return preDeviceCheckCompletionService.complete(missionId, deviceId);
    }



//
//
//    /**
//     * BATTERY fault auto-swap: find the next AVAILABLE device from the pool and
//     * assign it.
//     * If none available, fall back to RESOURCE_ASSIGNING for manager intervention.
//     */
//    private void attemptAutoSwapDevice(Mission mission, Device faultyDevice) {
//        String missionId = mission.getId();
//
//        // Release current assignment first
//        releaseFaultyDeviceAssignment(mission, faultyDevice, "BATTERY_FAIL");
//
//        // Try to find a replacement from the available pool
//        deviceRepository.findAll().stream()
//                .filter(candidate -> candidate.getStatus() == DeviceStatus.AVAILABLE)
//                .filter(candidate -> !candidate.getId().equals(faultyDevice.getId()))
//                .findFirst()
//                .ifPresentOrElse(replacementDevice -> {
//                    // Check for scheduling conflict
//                    boolean hasConflict = missionRepository.findActiveBydeviceId(replacementDevice.getId())
//                            .stream().anyMatch(m -> !m.getId().equals(missionId));
//
//                    if (hasConflict) {
//                        log.warn(
//                                "[AUTO-SWAP] Candidate device {} has a schedule conflict – falling back to RESOURCE_ASSIGNING.",
//                                replacementDevice.getDeviceCode());
//                        mission.setStatus(MissionStatus.RESOURCE_ASSIGNING);
//                        return;
//                    }
//
//                    // Assign replacement device
//                    replacementDevice.setStatus(DeviceStatus.RESERVED);
//                    deviceRepository.save(replacementDevice);
//
//                    MissiondeviceAssignment newMda = new MissiondeviceAssignment();
//                    newMda.setMission(mission);
//                    newMda.setDevice(replacementDevice);
//                    newMda.setStatus("ACTIVE");
//                    newMda.setIsCurrent(true);
//                    newMda.setAssignedAt(Instant.now());
//                    missiondeviceAssignmentRepository.save(newMda);
//
//                    // Mission returns to RESOURCE_ASSIGNING so manager can confirm before
//                    // re-dispatch
//                    mission.setStatus(MissionStatus.RESOURCE_ASSIGNING);
//                    log.info(
//                            "[AUTO-SWAP] Mission {} – swapped faulty device {} → replacement device {} (AUTO_SYSTEM). Mission → RESOURCE_ASSIGNING.",
//                            missionId, faultyDevice.getDeviceCode(), replacementDevice.getDeviceCode());
//
//                }, () -> {
//                    // No available replacement in the pool
//                    mission.setStatus(MissionStatus.RESOURCE_ASSIGNING);
//                    log.warn(
//                            "[AUTO-SWAP] Mission {} – no AVAILABLE devices in pool. Mission → RESOURCE_ASSIGNING for manager intervention.",
//                            missionId);
//                });
//    }

//    private void attemptAutoSwapdevice(Mission mission, Device faultydevice) {
//        attemptAutoSwapDevice(mission, faultydevice);
//    }

    private FlightToken issueFlightToken(String missionId, String deviceId, String staffId) {
        return flightTokenService.issueFlightToken(missionId, deviceId, staffId);
    }




    private void requireFeasiblePlan(MissionPlan plan) {
        requireFeasiblePlan(plan, false);
    }

    private MissionStaffAssignment requireCurrentStaffAssignment(String missionId, String staffId) {
        return requireCurrentStaffAssignments(missionId, staffId).stream()
                .filter(entry -> entry.getAssignedRole() == MissionStaffRole.PILOT
                        && entry.getResponseStatus() == StaffResponseStatus.ACCEPTED)
                .findFirst()
                .orElseThrow(() -> new ApiException(ErrorCode.ACCESS_DENIED,
                        "Staff is not assigned to mission " + missionId));
    }

    private List<MissionStaffAssignment> requireCurrentStaffAssignments(String missionId, String staffId) {
        List<MissionStaffAssignment> assignments = missionStaffAssignmentRepository
                .findAllByMissionIdAndStaffIdAndIsCurrentTrueOrderByAssignedAtDesc(missionId, staffId);
        if (assignments.isEmpty()) {
            throw new ApiException(ErrorCode.ACCESS_DENIED,
                    "Staff is not assigned to mission " + missionId);
        }
        return assignments;
    }

    private void requireCrewConfirmationStatus(Mission mission) {
        if (mission.getStatus() != MissionStatus.WAITING_CREW_CONFIRMATION
                && mission.getStatus() != MissionStatus.WAITING_OPERATOR_ACCEPTANCE) {
            throw new ApiException(ErrorCode.MISSION_STATUS_INVALID,
                    "Mission must be waiting for crew confirmation, current: " + mission.getStatus());
        }
    }

    private boolean hasRequiredCrewAssigned(String missionId) {
        Set<MissionStaffRole> roles = missionStaffAssignmentRepository.findAllByMissionIdAndIsCurrentTrue(missionId)
                .stream()
                .map(MissionStaffAssignment::getAssignedRole)
                .collect(java.util.stream.Collectors.toSet());
        return roles.containsAll(REQUIRED_CREW_ROLES);
    }

    private boolean isRequiredCrewAccepted(String missionId) {
        List<MissionStaffAssignment> assignments =
                missionStaffAssignmentRepository.findAllByMissionIdAndIsCurrentTrue(missionId);
        Set<MissionStaffRole> acceptedRoles = assignments.stream()
                .filter(assignment -> assignment.getResponseStatus() == StaffResponseStatus.ACCEPTED)
                .map(MissionStaffAssignment::getAssignedRole)
                .collect(java.util.stream.Collectors.toSet());
        return acceptedRoles.containsAll(REQUIRED_CREW_ROLES)
                && assignments.stream().allMatch(entry -> entry.getResponseStatus() == StaffResponseStatus.ACCEPTED);
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
            case BATTERY_DATA_UNAVAILABLE ->
                "Device battery telemetry is unavailable. Connect the device and refresh telemetry before pre-device.";
            case INSUFFICIENT_BATTERY -> "Device battery is insufficient for this mission and its safety reserve.";
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
                || mission.getStatus() == MissionStatus.WAITING_CREW_CONFIRMATION
                || mission.getStatus() == MissionStatus.WAITING_OPERATOR_ACCEPTANCE
                || mission.getStatus() == MissionStatus.CANCELLED) {
            return;
        }
        if (getCurrentDevice(mission.getId()) == null
                || missionPlanRepository.findByMissionId(mission.getId()).isPresent()) {
            return;
        }

        missionPlanningService.generateAStarEnergyAwarePlan(mission.getId());
    }
//
//    @Override
//    @Transactional
//    public MissionResponse replacedevice(String missionId, String newDeviceCode) {
//        Mission mission = getOrThrow(missionId);
//        Device newDevice = deviceRepository.findByDeviceCode(newDeviceCode)
//                .orElseThrow(() -> new ApiException(ErrorCode.DEVICE_NOT_AVAILABLE,
//                        "Device " + newDeviceCode + " không tồn tại"));
//
//        if (newDevice.getStatus() != DeviceStatus.AVAILABLE) {
//            throw new ApiException(ErrorCode.DEVICE_NOT_AVAILABLE,
//                    "Device " + newDeviceCode + " is not AVAILABLE (status: " + newDevice.getStatus() + ")");
//        }
//
//        List<Mission> activeMissions = missionRepository.findActiveBydeviceId(newDevice.getId());
//        boolean hasConflict = activeMissions.stream().anyMatch(m -> !m.getId().equals(missionId));
//        if (hasConflict) {
//            throw new ApiException(ErrorCode.SCHEDULE_CONFLICT,
//                    "Device " + newDeviceCode + " đang được lên lịch cho chuyến bay khác");
//        }
//
//        Device oldDevice = getCurrentDevice(missionId);
//        if (oldDevice != null) {
//            oldDevice.setStatus(DeviceStatus.MAINTENANCE);
//            deviceRepository.save(oldDevice);
//
//            // Release old MissiondeviceAssignment
//            missiondeviceAssignmentRepository.findByMissionIdAndIsCurrentTrue(missionId)
//                    .ifPresent(mda -> {
//                        mda.setIsCurrent(false);
//                        mda.setStatus("RELEASED");
//                        mda.setReleaseReason("PREFLIGHT_FAIL");
//                        mda.setReleasedAt(Instant.now());
//                        missiondeviceAssignmentRepository.save(mda);
//                    });
//        }
//
//        newDevice.setStatus(DeviceStatus.PREFLIGHT);
//        deviceRepository.save(newDevice);
//
//        // Record new MissiondeviceAssignment
//        MissiondeviceAssignment newMda = new MissiondeviceAssignment();
//        newMda.setMission(mission);
//        newMda.setDevice(newDevice);
//        newMda.setStatus("ACTIVE");
//        newMda.setIsCurrent(true);
//        newMda.setAssignedAt(Instant.now());
//        missiondeviceAssignmentRepository.save(newMda);
//
//        mission.setStatus(MissionStatus.CONNECTED);
//        log.info("Mission {} – replaced device with {}, status reset to CONNECTED", missionId, newDeviceCode);
//        Mission saved = missionRepository.save(mission);
//        return missionMapper.toResponse(saved);
//    }

//    @Override
//    @Transactional
//    public MissionResponse replacedevice(String missionId, String newDeviceCode) {
//        Mission mission = getOrThrow(missionId);
//        Device newDevice = deviceRepository.findByDeviceCode(newDeviceCode)
//                .orElseThrow(() -> new ApiException(ErrorCode.DEVICE_NOT_AVAILABLE,
//                        "Device " + newDeviceCode + " không tồn tại"));
//        if (newDevice.getStatus() != DeviceStatus.AVAILABLE) {
//            throw new ApiException(ErrorCode.DEVICE_NOT_AVAILABLE,
//                    "Device " + newDeviceCode + " is not AVAILABLE (status: " + newDevice.getStatus() + ")");
//        }
//        missiondeviceAssignmentRepository.findByMissionIdAndIsCurrentTrue(missionId)
//                .ifPresent(current -> {
//                    current.setIsCurrent(false);
//                    current.setStatus("RELEASED");
//                    current.setReleaseReason("MANUAL_REPLACE");
//                    current.setReleasedAt(Instant.now());
//                    missiondeviceAssignmentRepository.save(current);
//                });
//        newDevice.setStatus(DeviceStatus.PREFLIGHT);
//        deviceRepository.save(newDevice);
//        MissiondeviceAssignment assignment = new MissiondeviceAssignment();
//        assignment.setMission(mission);
//        assignment.setDevice(newDevice);
//        assignment.setStatus("ACTIVE");
//        assignment.setIsCurrent(true);
//        assignment.setAssignedAt(Instant.now());
//        missiondeviceAssignmentRepository.save(assignment);
//        mission.setStatus(MissionStatus.CONNECTED);
//        return missionMapper.toResponse(missionRepository.save(mission));
//    }
    private void validateStaffSchedule(String staffId, Instant missionStart, Instant missionEnd, Instant paddedStart,
                                       Instant paddedEnd) {
        List<UserSchedule> schedules = userScheduleRepository.findByStaffId(staffId);
        for (UserSchedule schedule : schedules) {
            if (schedule.getStatus() == UserScheduleStatus.CANCELLED
                    || schedule.getStatus() == UserScheduleStatus.COMPLETED) {
                continue;
            }

            if (schedule.getStatus() == UserScheduleStatus.ON_LEAVE
                    || schedule.getScheduleType() == UserScheduleType.LEAVE) {
                LocalDate leaveStart = schedule.getStartTime().atZone(ZoneOffset.UTC).toLocalDate();
                LocalDate leaveEnd = schedule.getEndTime().atZone(ZoneOffset.UTC).toLocalDate();
                LocalDate missionStartDay = missionStart.atZone(ZoneOffset.UTC).toLocalDate();
                LocalDate missionEndDay = missionEnd.atZone(ZoneOffset.UTC).toLocalDate();

                if (!leaveStart.isAfter(missionEndDay) && !leaveEnd.isBefore(missionStartDay)) {
                    throw new ApiException(ErrorCode.SCHEDULE_CONFLICT,
                            "Staff is on leave during the mission date period.");
                }
            } else if (schedule.getScheduleType() == UserScheduleType.MISSION) {
                if (schedule.getStartTime().isBefore(paddedEnd) && schedule.getEndTime().isAfter(paddedStart)) {
                    throw new ApiException(ErrorCode.SCHEDULE_CONFLICT,
                            "Staff schedule conflicts with another assigned mission (1-hour buffer required).");
                }
            } else {
                if (schedule.getScheduleType() == UserScheduleType.MAINTENANCE) {
                    if (schedule.getStartTime().isBefore(missionEnd) && schedule.getEndTime().isAfter(missionStart)) {
                        throw new ApiException(ErrorCode.SCHEDULE_CONFLICT,
                                "Staff schedule conflicts with maintenance schedule.");
                    }
                } else if (schedule.getScheduleType() == UserScheduleType.SHIFT
                        || schedule.getScheduleType() == UserScheduleType.ON_CALL) {
                    if (schedule.getStartTime().isAfter(missionStart) || schedule.getEndTime().isBefore(missionEnd)) {
                        throw new ApiException(ErrorCode.SCHEDULE_CONFLICT,
                                "Mission time is outside the staff scheduled shift duration.");
                    }
                } else {
                    if (schedule.getStartTime().isBefore(missionEnd) && schedule.getEndTime().isAfter(missionStart)) {
                        throw new ApiException(ErrorCode.SCHEDULE_CONFLICT,
                                "Staff schedule conflicts with an existing schedule record.");
                    }
                }
            }
        }
    }
    @Override
    @Transactional
    @PreAuthorize("@missionAuthorizationService.canControlFlight(#missionId) and @missionAuthorizationService.canViewStaffMissions(#staffId)")
    public MissionResponse handoverControl(String missionId, String staffId) {
        Mission mission = getOrThrow(missionId);
        String resolvedMissionId = mission.getId();
        requireStatus(mission, MissionStatus.READY_TO_FLY);

        // Record ControlHandover audit log linked to current DeviceConnection
        DeviceConnection activeConnection = deviceConnectionRepository
                .findTopByMissionIdAndConnectionStatusOrderByConnectedAtDesc(resolvedMissionId, "CONNECTED")
                .orElse(null);

        ControlHandover handover = new ControlHandover();
        handover.setDeviceConnection(activeConnection);
        handover.setStaffAssignment(requireCurrentStaffAssignment(resolvedMissionId, staffId));
        handover.setStatus("CONFIRMED");
        handover.setAcknowledgementText("Staff confirmed control handover and pre-device checks before launch");
        handover.setConfirmedAt(Instant.now());
        controlHandoverRepository.save(handover);

        log.info("Mission {} – control handed over to Staff {} at {}", missionId, staffId, Instant.now());
        Mission saved = missionRepository.save(mission);
        return missionMapper.toResponse(saved);
    }

    // =========================================================================
    // F3.3 – Takeoff & Execution lifecycle
    // =========================================================================

    @Override
    @Transactional
    @PreAuthorize("@missionAuthorizationService.canControlFlight(#missionId)")
    public MissionResponse startMission(String missionId, String tokenValue) {
        Mission mission = getOrThrow(missionId);
        String resolvedMissionId = mission.getId();
        requireStatus(mission, MissionStatus.READY_TO_FLY);

        if (tokenValue != null && !tokenValue.isBlank()) {
            FlightToken token = flightTokenRepository.findByTokenValue(tokenValue)
                    .orElseThrow(() -> new ApiException(ErrorCode.INVALID_REQUEST, "Invalid flight access token"));

            if (!resolvedMissionId.equals(token.getMissionId())) {
                throw new ApiException(ErrorCode.ACCESS_DENIED, "Flight token belongs to another mission");
            }

            if (!token.isValid()) {
                token.setRevoked(true);
                flightTokenRepository.save(token);
                log.warn("Mission {} flight token EXPIRED or ALREADY USED – revoking token", missionId);
                throw new ApiException(ErrorCode.INVALID_REQUEST,
                        "Flight token is expired or revoked. Please re-run pre-device check.");
            }
            token.setUsed(true);
            flightTokenRepository.save(token);
        } else {
            FlightToken token = flightTokenRepository
                    .findAllByMissionIdAndUsedFalseAndRevokedFalseOrderByIssuedAtDesc(resolvedMissionId)
                    .stream()
                    .findFirst()
                    .orElse(null);
            if (token != null) {
                if (!token.isValid()) {
                    token.setRevoked(true);
                    flightTokenRepository.save(token);
                    throw new ApiException(ErrorCode.INVALID_REQUEST,
                            "Flight token is expired. Please re-run pre-device check.");
                }
                token.setUsed(true);
                flightTokenRepository.save(token);
            }
        }

        mission.setStatus(MissionStatus.IN_FLIGHT);
        mission.setActualStartAt(Instant.now());
        updateDeviceStatus(mission, DeviceStatus.ACTIVE_MISSION);

        log.info("Mission {} IN_FLIGHT – WebSocket telemetry and RTSP video stream OPENED", missionId);
        Mission saved = missionRepository.save(mission);
        return missionMapper.toResponse(saved);
    }

    @Override
    @Transactional
    @PreAuthorize("@missionAuthorizationService.canControlFlight(#missionId)")
    public MissionResponse startMission(String missionId) {
        return startMission(missionId, null);
    }

    @Override
    @Transactional
    @PreAuthorize("@missionAuthorizationService.canControlFlight(#missionId)")
    public MissionResponse markReturning(String missionId) {
        Mission mission = getOrThrow(missionId);
        if (mission.getStatus() != MissionStatus.IN_FLIGHT && mission.getStatus() != MissionStatus.IN_PROGRESS) {
            throw new ApiException(ErrorCode.MISSION_STATUS_INVALID,
                    "Mission must be IN_FLIGHT to mark returning, current: " + mission.getStatus());
        }
        mission.setStatus(MissionStatus.RETURNING);
        updateDeviceStatus(mission, DeviceStatus.RETURNING);
        log.info("Mission {} – device returning to base", missionId);
        Mission saved = missionRepository.save(mission);
        return missionMapper.toResponse(saved);
    }

    @Override
    @Transactional
    @PreAuthorize("@missionAuthorizationService.canControlFlight(#missionId)")
    public MissionResponse startPostDeviceChecking(String missionId) {
        Mission mission = getOrThrow(missionId);
        requireStatus(mission, MissionStatus.RETURNING);
        mission.setStatus(MissionStatus.POSTFLIGHT_CHECKING);
        log.info("Mission {} – post-device inspection started", missionId);
        Mission saved = missionRepository.save(mission);
        return missionMapper.toResponse(saved);
    }

    @Override
    @Transactional
    @PreAuthorize("@missionAuthorizationService.canControlFlight(#missionId)")
    public MissionResponse completeMission(String missionId) {
        Mission mission = getOrThrow(missionId);
        String resolvedMissionId = mission.getId();
        if (mission.getStatus() == MissionStatus.COMPLETED
                && postDeviceCheckRepository.findFirstByMissionIdOrderByCreatedAtDesc(resolvedMissionId).isPresent()) {
            return missionMapper.toResponse(mission);
        }
        if (mission.getStatus() != MissionStatus.POSTFLIGHT_CHECKING) {
            throw new ApiException(ErrorCode.MISSION_STATUS_INVALID,
                    "Mission must be POSTFLIGHT_CHECKING with a recorded inspection to complete but is "
                            + mission.getStatus());
        }
        if (postDeviceCheckRepository.findFirstByMissionIdOrderByCreatedAtDesc(resolvedMissionId).isEmpty()) {
            throw new ApiException(ErrorCode.MISSION_STATUS_INVALID,
                    "A recorded post-flight inspection is required before mission completion");
        }
        mission.setStatus(MissionStatus.COMPLETED);
        Instant completedAt = Instant.now();
        if (mission.getActualEndAt() == null) {
            mission.setActualEndAt(completedAt);
        }
        mission.setCompletedAt(completedAt);
        releaseMissionResources(mission, "MISSION_COMPLETE");
        log.info("Mission {} COMPLETED successfully", missionId);
        Mission saved = missionRepository.save(mission);
        missionResultService.ensureCompletedResult(saved);
        return missionMapper.toResponse(saved);
    }

    @Override
    @Transactional
    @PreAuthorize("@missionAuthorizationService.canControlFlight(#missionId)")
    public MissionResponse failMission(String missionId, String reason) {
        Mission mission = getOrThrow(missionId);
        if (mission.getStatus() == MissionStatus.IN_FLIGHT
                || mission.getStatus() == MissionStatus.RETURNING) {
            updateDeviceStatus(mission, DeviceStatus.MAINTENANCE);
        }
        mission.setStatus(MissionStatus.FAILED);
        mission.setActualEndAt(Instant.now());
        releaseMissionResources(mission, "MISSION_FAILED");
        log.error("Mission {} FAILED – reason: {}", missionId, reason);
        Mission saved = missionRepository.save(mission);
        return missionMapper.toResponse(saved);
    }

//    @Override
//    @Transactional
//    public MissionResponse failMission(String missionId, String reason) {
//        Mission mission = getOrThrow(missionId);
//        if (mission.getStatus() == MissionStatus.IN_FLIGHT
//                || mission.getStatus() == MissionStatus.RETURNING) {
//            updatedeviceStatus(mission, DeviceStatus.MAINTENANCE);
//        }
//        mission.setStatus(MissionStatus.FAILED);
//        mission.setFailureReason(reason);
//        releaseMissionResources(mission, "MISSION_FAILED");
//        log.error("Mission {} FAILED – reason: {}", missionId, reason);
//        Mission saved = missionRepository.save(mission);
//        return missionMapper.toResponse(saved);
//    }

    // =========================================================================
    // F3.5 – Post-flight device status update
    // =========================================================================

    @Override
    @Transactional
    @PreAuthorize("@missionAuthorizationService.canInspectDevice(#missionId)")
    public MissionResponse updatePostFlightStatus(String missionId, DeviceStatus newDeviceStatus, String notes) {
        return savePostFlightStatus(missionId, newDeviceStatus, notes, Map.of());
    }

    @Override
    @Transactional
    @PreAuthorize("@missionAuthorizationService.canInspectDevice(#missionId)")
    public MissionResponse recordPostFlightInspection(String missionId, DeviceStatus newDeviceStatus,
            String notes, Map<String, InspectionResult> results,
            PostFlightStatusRequest.TelemetrySnapshot telemetrySnapshot) {
        Set<String> required = Set.of("a1", "a2", "p1", "p2", "e1", "e2", "e3", "e4", "d1");
        if (results == null || !results.keySet().equals(required)
                || results.values().stream().anyMatch(value -> value == null)) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "All nine inspection items must have a result");
        }
        boolean faultDetected = results.containsValue(InspectionResult.FAIL);
        DeviceStatus expected = faultDetected ? DeviceStatus.MAINTENANCE : DeviceStatus.AVAILABLE;
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

    private MissionResponse savePostFlightStatus(String missionId, DeviceStatus newDeviceStatus,
            String notes, Map<String, InspectionResult> results) {
        return savePostFlightStatus(missionId, newDeviceStatus, notes, results, null);
    }

    private MissionResponse savePostFlightStatus(String missionId, DeviceStatus newDeviceStatus,
            String notes, Map<String, InspectionResult> results,
            PostFlightStatusRequest.TelemetrySnapshot telemetrySnapshot) {
        Mission mission = getOrThrow(missionId);
        Device device = getCurrentDevice(mission.getId());
        if (device == null) {
            throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Mission " + missionId + " has no assigned device");
        }
        device.setStatus(newDeviceStatus);
        deviceRepository.save(device);

        // Auto-create MaintenanceTicket if device requires maintenance post-flight
        if (newDeviceStatus == DeviceStatus.MAINTENANCE) {
            MaintenanceTicket ticket = new MaintenanceTicket();
            ticket.setTicketCode("TKT-POSTFLIGHT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
            ticket.setDevice(device); // device field — device-type agnostic
            ticket.setReportedBy(getCurrentStaffId(mission.getId()));
            ticket.setIssueType("POSTFLIGHT_DAMAGE");
            ticket.setSeverity("HIGH");
            ticket.setDescription("Post-flight physical inspection flagged maintenance needed for device "
                    + device.getDeviceCode() + ". Notes: " + notes);
            ticket.setStatus("OPEN");
            ticket.setOpenedAt(Instant.now());
            maintenanceTicketRepository.save(ticket);
            log.error("[MAINTENANCE] Auto-created MaintenanceTicket {} for device {}", ticket.getTicketCode(),
                    device.getDeviceCode());
        }

        persistedPostDeviceCheckService.recordInspection(mission.getId(), results, telemetrySnapshot);
        if (mission.getStatus() == MissionStatus.POSTFLIGHT_CHECKING) {
            mission.setStatus(MissionStatus.COMPLETED);
            Instant completedAt = Instant.now();
            if (mission.getActualEndAt() == null) {
                mission.setActualEndAt(completedAt);
            }
            mission.setCompletedAt(completedAt);
            releaseMissionResources(mission, "MISSION_COMPLETE");
        }

        if (notes != null && !notes.isBlank()) {
            log.info("Mission {} post-flight notes: {}", missionId, notes);
        }
        log.info("Mission {} post-flight completed – device {} status set to {}", missionId, device.getDeviceCode(),
                newDeviceStatus);
        Mission saved = missionRepository.save(mission);
        return missionMapper.toResponse(saved);
    }

    private void requireScheduledWindow(Mission mission) {
        if (mission.getScheduledStartAt() == null || mission.getScheduledEndAt() == null) {
            throw new ApiException(ErrorCode.INVALID_REQUEST,
                    "Mission schedule start and end times must be set before assigning resources.");
        }
    }

//    private void moveToWaitingStaffAcceptanceIfReady(Mission mission) {
//        boolean hasDevice = missiondeviceAssignmentRepository.findByMissionIdAndIsCurrentTrue(mission.getId()).isPresent();
//        boolean hasStaff = !missionStaffAssignmentRepository.findByMissionId(mission.getId()).isEmpty();
//        if (hasDevice && hasStaff) {
//            mission.setStatus(MissionStatus.WAITING_OPERATOR_ACCEPTANCE);
//        }
//    }



    private void validateDeviceSchedule(String deviceId, Mission mission) {
        Instant paddedStart = mission.getScheduledStartAt().minus(1, ChronoUnit.HOURS);
        Instant paddedEnd = mission.getScheduledEndAt().plus(1, ChronoUnit.HOURS);

        List<Mission> activeMissions = missionRepository.findActiveByDeviceId(deviceId);
        for (Mission activeMission : activeMissions) {
            if (activeMission.getId().equals(mission.getId())) {
                throw new ApiException(ErrorCode.RESOURCE_ALREADY_EXISTS,
                        "Device is already assigned to this mission.");
            }
            if (activeMission.getScheduledStartAt() != null
                    && activeMission.getScheduledEndAt() != null
                    && activeMission.getScheduledStartAt().isBefore(paddedEnd)
                    && activeMission.getScheduledEndAt().isAfter(paddedStart)) {
                throw new ApiException(ErrorCode.SCHEDULE_CONFLICT,
                        "Device schedule conflicts with an existing mission (1-hour buffer required)");
            }
        }
    }

    private void validateStaffSchedule(String staffId, Mission mission) {
        Instant missionStart = mission.getScheduledStartAt();
        Instant missionEnd = mission.getScheduledEndAt();
        Instant paddedStart = missionStart.minus(1, ChronoUnit.HOURS);
        Instant paddedEnd = missionEnd.plus(1, ChronoUnit.HOURS);

        List<UserSchedule> schedules = userScheduleRepository.findByStaffId(staffId);
        for (UserSchedule schedule : schedules) {
            if (schedule.getStatus() == UserScheduleStatus.CANCELLED
                    || schedule.getStatus() == UserScheduleStatus.COMPLETED) {
                continue;
            }

            if (schedule.getStatus() == UserScheduleStatus.ON_LEAVE
                    || schedule.getScheduleType() == UserScheduleType.LEAVE) {
                LocalDate leaveStart = schedule.getStartTime().atZone(ZoneOffset.UTC).toLocalDate();
                LocalDate leaveEnd = schedule.getEndTime().atZone(ZoneOffset.UTC).toLocalDate();
                LocalDate missionStartDay = missionStart.atZone(ZoneOffset.UTC).toLocalDate();
                LocalDate missionEndDay = missionEnd.atZone(ZoneOffset.UTC).toLocalDate();

                if (!leaveStart.isAfter(missionEndDay) && !leaveEnd.isBefore(missionStartDay)) {
                    throw new ApiException(ErrorCode.SCHEDULE_CONFLICT,
                            "Staff is on leave during the mission date period.");
                }
            } else if (schedule.getScheduleType() == UserScheduleType.MISSION) {
                if (schedule.getStartTime().isBefore(paddedEnd) && schedule.getEndTime().isAfter(paddedStart)) {
                    throw new ApiException(ErrorCode.SCHEDULE_CONFLICT,
                            "Staff schedule conflicts with another assigned mission (1-hour buffer required).");
                }
            } else if (schedule.getScheduleType() == UserScheduleType.MAINTENANCE) {
                if (schedule.getStartTime().isBefore(missionEnd) && schedule.getEndTime().isAfter(missionStart)) {
                    throw new ApiException(ErrorCode.SCHEDULE_CONFLICT,
                            "Staff schedule conflicts with maintenance schedule.");
                }
            } else if (schedule.getScheduleType() == UserScheduleType.SHIFT
                    || schedule.getScheduleType() == UserScheduleType.ON_CALL) {
                if (schedule.getStartTime().isAfter(missionStart) || schedule.getEndTime().isBefore(missionEnd)) {
                    throw new ApiException(ErrorCode.SCHEDULE_CONFLICT,
                            "Mission time is outside the staff scheduled shift duration.");
                }
            } else if (schedule.getStartTime().isBefore(missionEnd)
                    && schedule.getEndTime().isAfter(missionStart)) {
                throw new ApiException(ErrorCode.SCHEDULE_CONFLICT,
                        "Staff schedule conflicts with an existing schedule record.");
            }
        }
    }

    private void validateScheduledDates(Instant startAt, Instant endAt, Order order) {
        if (startAt == null || endAt == null) {
            throw new ApiException(ErrorCode.INVALID_REQUEST,
                    "Scheduled start time and scheduled end time must be specified");
        }
        if (!startAt.isBefore(endAt)) {
            throw new ApiException(ErrorCode.INVALID_REQUEST,
                    "Scheduled start time must be before scheduled end time");
        }

        LocalDate startDate = startAt.atZone(ZoneOffset.UTC).toLocalDate();
        LocalDate endDate = endAt.atZone(ZoneOffset.UTC).toLocalDate();
        if (!startDate.equals(endDate)) {
            throw new ApiException(ErrorCode.INVALID_REQUEST,
                    "Scheduled start time and scheduled end time must be on the same day");
        }
        if (order != null && order.getPreferredDateFrom() != null
                && startDate.isBefore(order.getPreferredDateFrom())) {
            throw new ApiException(ErrorCode.INVALID_REQUEST,
                    "Scheduled date must be greater than or equal to preferred start date: "
                            + order.getPreferredDateFrom());
        }
    }

    private String generateMissionCode() {
        String code;
        do {
            int number = ThreadLocalRandom.current().nextInt(100000);
            code = String.format("MS-%05d", number);
        } while (missionRepository.existsByMissionCode(code));
        return code;
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private Mission getOrThrow(String missionId) {
        return missionRepository.findById(missionId)
                .or(() -> missionRepository.findByOrderId(missionId))
                .orElseThrow(() -> new ApiException(ErrorCode.MISSION_NOT_FOUND,
                        "Mission not found: " + missionId));
    }

    private String currentStaffId() {
        User staff = authenticatedUserResolver.getCurrentUser();
        if (staff.getRole() == null || staff.getRole().getCode() != RoleCode.STAFF) {
            throw new ApiException(ErrorCode.ACCESS_DENIED, "A staff account is required");
        }
        return staff.getId().toString();
    }

    private void requireStatus(Mission mission, MissionStatus expected) {
        if (mission.getStatus() != expected) {
            throw new ApiException(ErrorCode.MISSION_STATUS_INVALID,
                    "Mission status must be " + expected + " but is " + mission.getStatus());
        }
    }

    private void updateDeviceStatus(Mission mission, DeviceStatus newStatus) {
        Device device = getCurrentDevice(mission.getId());
        if (device != null) {
            device.setStatus(newStatus);
            deviceRepository.save(device);
        }
    }

    private boolean isReleasedLock(ResourceTimeLock lock) {
        return lock != null
                && lock.getMission() != null
                && isTerminalMission(lock.getMission().getStatus());
    }

    private boolean isTerminalMission(MissionStatus status) {
        return status == MissionStatus.COMPLETED
                || status == MissionStatus.FAILED
                || status == MissionStatus.CANCELLED;
    }

    private void releaseMissionResources(Mission mission, String reason) {
        Instant releasedAt = Instant.now();
        String missionId = mission.getId();

        Device device = getCurrentDevice(mission.getId());
        if (device != null) {
            if (device.getStatus() != DeviceStatus.MAINTENANCE) {
                device.setStatus(DeviceStatus.AVAILABLE);
            }
            deviceRepository.save(device);
        }

        List<MissionDeviceAssignment> deviceAssignments = missionDeviceAssignmentRepository.findByMissionId(missionId);
        for (MissionDeviceAssignment assignment : deviceAssignments) {
            if (assignment.getReleasedAt() == null) {
                assignment.setReleasedAt(releasedAt);
            }
            assignment.setReleaseReason(reason);
            if (assignment.getDevice() != null && assignment.getDevice().getId() != null) {
                resourceTimeLockRepository
                        .deleteAll(resourceTimeLockRepository.findAllByResourceIdAndMissionId(
                                assignment.getDevice().getId(),
                                missionId));
            }
        }
        if (!deviceAssignments.isEmpty()) {
            missionDeviceAssignmentRepository.saveAll(deviceAssignments);
        }

        List<MissionStaffAssignment> staffAssignments = missionStaffAssignmentRepository.findByMissionId(missionId);
        for (MissionStaffAssignment assignment : staffAssignments) {
            if (assignment.getRespondedAt() == null) {
                assignment.setRespondedAt(releasedAt);
            }
            if (assignment.getReleasedAt() == null) {
                assignment.setReleasedAt(releasedAt);
            }
            assignment.setReleaseReason(reason);
            if (assignment.getStaff() != null && assignment.getStaff().getId() != null) {
                resourceTimeLockRepository
                        .deleteAll(resourceTimeLockRepository.findAllByResourceIdAndMissionId(
                                assignment.getStaff().getId().toString(),
                                missionId));
            }
        }
        if (!staffAssignments.isEmpty()) {
            missionStaffAssignmentRepository.saveAll(staffAssignments);
        }

        UserScheduleStatus releasedScheduleStatus = "MISSION_COMPLETE".equals(reason)
                ? UserScheduleStatus.COMPLETED
                : UserScheduleStatus.CANCELLED;
        List<UserSchedule> schedules = userScheduleRepository.findByReferenceId(missionId);
        for (UserSchedule schedule : schedules) {
            if (schedule.getScheduleType() == UserScheduleType.MISSION
                    && schedule.getStatus() != UserScheduleStatus.COMPLETED
                    && schedule.getStatus() != UserScheduleStatus.CANCELLED) {
                schedule.setStatus(releasedScheduleStatus);
                schedule.setNotes(appendReleaseNote(schedule.getNotes(), reason));
            }
        }
        if (!schedules.isEmpty()) {
            userScheduleRepository.saveAll(schedules);
        }
    }

    private String appendReleaseNote(String notes, String reason) {
        String releaseNote = "Released: " + reason;
        if (notes == null || notes.isBlank()) {
            return releaseNote;
        }
        if (notes.contains(releaseNote)) {
            return notes;
        }
        return notes + " | " + releaseNote;
    }

    private Device getCurrentDevice(String missionId) {
        return missionDeviceAssignmentRepository.findFirstByMissionIdOrderByCreatedAtDesc(missionId)
                .map(MissionDeviceAssignment::getDevice)
                .orElse(null);
    }

    private String getCurrentStaffId(String missionId) {
        return missionStaffAssignmentRepository.findFirstByMissionIdOrderByAssignedAtDesc(missionId)
                .map(assignment -> assignment.getStaff() != null && assignment.getStaff().getId() != null
                        ? assignment.getStaff().getId().toString()
                        : null)
                .orElse(null);
    }

}
