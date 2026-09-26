package com.ondemandmonitoring.missionv2.service.impl;

import com.ondemandmonitoring.common.exception.ApiException;
import com.ondemandmonitoring.common.exception.ErrorCode;
import com.ondemandmonitoring.device.domain.Device;
import com.ondemandmonitoring.device.enums.DeviceStatus;
import com.ondemandmonitoring.missionv2.enums.CheckupStatus;
import com.ondemandmonitoring.missionv2.enums.DeviceRole;
import com.ondemandmonitoring.missionv2.enums.LockStatus;
import com.ondemandmonitoring.missionv2.enums.MissionV2Status;
import com.ondemandmonitoring.missionv2.enums.StaffResponseStatus;
import com.ondemandmonitoring.missionv2.domain.MissionDeviceAssignment;
import com.ondemandmonitoring.missionv2.domain.MissionRescheduleHistory;
import com.ondemandmonitoring.missionv2.domain.MissionStaffAssignment;
import com.ondemandmonitoring.missionv2.domain.MissionV2;
import com.ondemandmonitoring.missionv2.domain.ResourceTimeLock;
import com.ondemandmonitoring.missionv2.dto.request.AssignDeviceRequest;
import com.ondemandmonitoring.missionv2.dto.request.AssignStaffRequest;
import com.ondemandmonitoring.missionv2.dto.request.MissionCreateRequest;
import com.ondemandmonitoring.missionv2.dto.request.MissionUpdateRequest;
import com.ondemandmonitoring.missionv2.dto.response.MissionV2Response;
import com.ondemandmonitoring.missionv2.mapper.MissionV2Mapper;
import com.ondemandmonitoring.missionv2.repository.MissionStaffAssignmentRepository;
import com.ondemandmonitoring.missionv2.repository.ResourceTimeLockRepository;
import com.ondemandmonitoring.missionv2.service.IMissionV2Service;
import com.ondemandmonitoring.order.domain.Order;
import com.ondemandmonitoring.order.enums.OrderStatus;
import com.ondemandmonitoring.order.repository.OrderRepository;
import com.ondemandmonitoring.user.domain.User;
import com.ondemandmonitoring.user.repository.UserRepository;
import com.ondemandmonitoring.user.service.AuthenticatedUserResolver;
import com.ondemandmonitoring.userschedule.domain.UserSchedule;
import com.ondemandmonitoring.userschedule.enums.UserScheduleStatus;
import com.ondemandmonitoring.userschedule.enums.UserScheduleType;
import com.ondemandmonitoring.userschedule.repository.UserScheduleRepository;
import com.ondemandmonitoring.missionv2.repository.MissionV2Repository;
import com.ondemandmonitoring.missionv2.repository.MissionDeviceAssignmentRepository;
import com.ondemandmonitoring.missionv2.repository.MissionRescheduleHistoryRepository;
import com.ondemandmonitoring.device.repository.DeviceRepository;
import com.ondemandmonitoring.planning.service.MissionPlanningService;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class MissionV2Service implements IMissionV2Service {

    MissionV2Repository missionV2Repository;
    MissionV2Mapper missionMapper;
    OrderRepository orderRepository;
    ResourceTimeLockRepository resourceTimeLockRepository;
    UserRepository userRepository;
    MissionStaffAssignmentRepository missionStaffAssignmentRepository;
    UserScheduleRepository userScheduleRepository;
    AuthenticatedUserResolver authenticatedUserResolver;
    MissionRescheduleHistoryRepository missionRescheduleHistoryRepository;
    DeviceRepository deviceRepository;
    MissionDeviceAssignmentRepository missionDeviceAssignmentRepository;
    MissionPlanningService missionPlanningService;

    @Override
    @Transactional
    public MissionV2Response createMission(MissionCreateRequest request) {
        if (missionV2Repository.existsByOrderId(request.getOrderId())) {
            throw new ApiException(ErrorCode.RESOURCE_ALREADY_EXISTS,
                    "Mission already exists for order id: " + request.getOrderId());
        }

        Order order = orderRepository.findById(request.getOrderId())
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND,
                        "Order not found with id: " + request.getOrderId()));

        if (order.getOrderStatus() != OrderStatus.APPROVED) {
            throw new ApiException(ErrorCode.INVALID_REQUEST,
                    "Order status must be APPROVED to create a mission");
        }

        Instant startAt = request.getScheduledStartAt();
        Instant endAt = request.getScheduledEndAt();

        validateScheduledDates(startAt, endAt, order);

        MissionV2 mission = new MissionV2();
        mission.setMissionCode(generateMissionCode());
        mission.setStatus(MissionV2Status.RESOURCE_ASSIGNING);
        mission.setOrder(order);
        mission.setScheduledStartAt(startAt);
        mission.setScheduledEndAt(endAt);

        MissionV2 saved = missionV2Repository.save(mission);
        missionPlanningService.generateAStarEnergyAwarePlan(mission.getId());
        return missionMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public MissionV2Response updateMission(String missionId, MissionUpdateRequest request) {
        MissionV2 mission = missionV2Repository.findById(missionId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND,
                        "Mission not found with id: " + missionId));

        Instant previousStart = mission.getScheduledStartAt();
        Instant previousEnd = mission.getScheduledEndAt();

        Instant newStart = request.getScheduledStartAt() != null ? request.getScheduledStartAt() : previousStart;
        Instant newEnd = request.getScheduledEndAt() != null ? request.getScheduledEndAt() : previousEnd;

        validateScheduledDates(newStart, newEnd, mission.getOrder());

        mission.setScheduledStartAt(newStart);
        mission.setScheduledEndAt(newEnd);

        MissionV2 saved = missionV2Repository.save(mission);

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

    @Override
    @Transactional(readOnly = true)
    public List<MissionV2Response> getAllMissions() {
        return missionV2Repository.findAll().stream()
                .map(missionMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public MissionV2Response assignDevice(String missionId, AssignDeviceRequest request) {
        String deviceId = request.getDeviceId();
        DeviceRole deviceRole = request.getDeviceRole();
        MissionV2 mission = missionV2Repository.findById(missionId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND,
                        "Mission not found with id: " + missionId));
        if (mission.getStatus() != MissionV2Status.RESOURCE_ASSIGNING) {
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

        List<ResourceTimeLock> existingLocks = resourceTimeLockRepository.findByResourceId(deviceId);
        for (ResourceTimeLock lock : existingLocks) {
            if (lock.getMission() != null && lock.getMission().getId().equals(missionId)) {
                throw new ApiException(ErrorCode.RESOURCE_ALREADY_EXISTS,
                        "Device is already assigned to this mission.");
            }
            if (lock.getStartTime() != null && lock.getEndTime() != null) {
                if (lock.getStartTime().isBefore(paddedEnd) && lock.getEndTime().isAfter(paddedStart)) {
                    throw new ApiException(ErrorCode.SCHEDULE_CONFLICT,
                            "Device schedule conflicts with an existing resource lock (1-hour buffer required)");
                }
            }
        }

        if (resourceTimeLockRepository.findByResourceIdAndMissionId(deviceId, missionId).isPresent()) {
            throw new ApiException(ErrorCode.RESOURCE_ALREADY_EXISTS,
                    "Device is already assigned to this mission.");
        }

        ResourceTimeLock lock = ResourceTimeLock.builder()
                .resourceId(deviceId)
                .mission(mission)
                .startTime(missionStart)
                .endTime(missionEnd)
                .lockStatus(LockStatus.HARD_LOCK)
                .expiresAt(null)
                .build();
        resourceTimeLockRepository.save(lock);

        DeviceRole role = (deviceRole != null) ? deviceRole : DeviceRole.MAIN;
        MissionDeviceAssignment newAssignment = MissionDeviceAssignment.builder()
                .mission(mission)
                .device(device)
                .deviceRole(role)
                .checkupStatus(CheckupStatus.PENDING)
                .postcheckStatus(null)
                .verifiedBy(null)
                .verifiedAt(null)
                .failureNotes(null)
                .build();

        missionDeviceAssignmentRepository.save(newAssignment);
        MissionV2 saved = missionV2Repository.save(mission);
        return missionMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public MissionV2Response assignStaff(String missionId, AssignStaffRequest request) {
        String staffId = request.getStaffId();
        String assignedRole = request.getAssignedRole() != null ? request.getAssignedRole() : "STAFF";

        MissionV2 mission = missionV2Repository.findById(missionId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND,
                        "Mission not found with id: " + missionId));
        if (mission.getStatus() != MissionV2Status.RESOURCE_ASSIGNING) {
            throw new ApiException(ErrorCode.INVALID_REQUEST,
                    "Mission status must be RESOURCE_ASSIGNING to assign a device.");
        }

        User staff = userRepository.findById(staffId)
                .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND, "Staff not found with id: " + staffId));

        Instant missionStart = mission.getScheduledStartAt();
        Instant missionEnd = mission.getScheduledEndAt();

        if (missionStart == null || missionEnd == null) {
            throw new ApiException(ErrorCode.INVALID_REQUEST,
                    "Mission schedule start and end times must be set before assigning staff.");
        }

        Instant paddedStart = missionStart.minus(1, ChronoUnit.HOURS);
        Instant paddedEnd = missionEnd.plus(1, ChronoUnit.HOURS);

        if (missionStaffAssignmentRepository.findByMissionIdAndStaffId(missionId, staffId).isPresent()) {
            throw new ApiException(ErrorCode.RESOURCE_ALREADY_EXISTS,
                    "Staff is already assigned to this mission.");
        }

        validateStaffSchedule(staffId, missionStart, missionEnd, paddedStart, paddedEnd);

        List<ResourceTimeLock> existingLocks = resourceTimeLockRepository.findByResourceId(staffId);
        for (ResourceTimeLock lock : existingLocks) {
            if (lock.getMission() != null && lock.getMission().getId().equals(missionId)) {
                throw new ApiException(ErrorCode.RESOURCE_ALREADY_EXISTS,
                        "Staff is already assigned to this mission.");
            }
            if (lock.getStartTime() != null && lock.getEndTime() != null) {
                if (lock.getStartTime().isBefore(paddedEnd) && lock.getEndTime().isAfter(paddedStart)) {
                    throw new ApiException(ErrorCode.SCHEDULE_CONFLICT,
                            "Staff schedule conflicts with an existing resource lock (1-hour buffer required)");
                }
            }
        }

        if (resourceTimeLockRepository.findByResourceIdAndMissionId(staffId, missionId).isPresent()) {
            throw new ApiException(ErrorCode.RESOURCE_ALREADY_EXISTS,
                    "Staff is already assigned to this mission.");
        }

        ResourceTimeLock lock = ResourceTimeLock.builder()
                .resourceId(staffId)
                .mission(mission)
                .startTime(missionStart)
                .endTime(missionEnd)
                .lockStatus(LockStatus.HARD_LOCK)
                .expiresAt(missionStart.minus(1, ChronoUnit.HOURS))
                .build();
        resourceTimeLockRepository.save(lock);

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
                .notes("Assigned to mission " + mission.getMissionCode())
                .build();
        userScheduleRepository.save(staffSchedule);

        MissionV2 saved = missionV2Repository.save(mission);
        return missionMapper.toResponse(saved);
    }

    private void validateStaffSchedule(String staffId, Instant missionStart, Instant missionEnd, Instant paddedStart,
            Instant paddedEnd) {
        List<UserSchedule> schedules = userScheduleRepository.findByStaffId(staffId);
        for (UserSchedule schedule : schedules) {
            if (schedule.getStatus() == UserScheduleStatus.CANCELLED) {
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

        if (order != null && order.getPreferredDateFrom() != null) {
            if (startDate.isBefore(order.getPreferredDateFrom())) {
                throw new ApiException(ErrorCode.INVALID_REQUEST,
                        "Scheduled date must be greater than or equal to preferred start date: "
                                + order.getPreferredDateFrom());
            }
        }
    }

    private String generateMissionCode() {
        String code;
        do {
            int number = ThreadLocalRandom.current().nextInt(100000);
            code = String.format("MS-%05d", number);
        } while (missionV2Repository.existsByMissionCode(code));
        return code;
    }
}
