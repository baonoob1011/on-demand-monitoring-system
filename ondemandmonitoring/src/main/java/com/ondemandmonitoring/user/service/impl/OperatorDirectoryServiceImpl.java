package com.ondemandmonitoring.user.service.impl;

import com.ondemandmonitoring.common.exception.ApiException;
import com.ondemandmonitoring.common.exception.ErrorCode;
import com.ondemandmonitoring.mission.domain.Mission;
import com.ondemandmonitoring.mission.domain.ResourceTimeLock;
import com.ondemandmonitoring.mission.enums.MissionStatus;
import com.ondemandmonitoring.mission.repository.MissionRepository;
import com.ondemandmonitoring.mission.repository.ResourceTimeLockRepository;
import com.ondemandmonitoring.role.domain.RoleCode;
import com.ondemandmonitoring.user.domain.User;
import com.ondemandmonitoring.user.dto.response.AvailableOperatorResponse;
import com.ondemandmonitoring.user.repository.UserRepository;
import com.ondemandmonitoring.user.service.IOperatorDirectoryService;
import com.ondemandmonitoring.userschedule.domain.UserSchedule;
import com.ondemandmonitoring.userschedule.enums.UserScheduleStatus;
import com.ondemandmonitoring.userschedule.repository.UserScheduleRepository;
import java.time.Instant;
import java.util.List;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class OperatorDirectoryServiceImpl implements IOperatorDirectoryService {

    UserRepository userRepository;
    MissionRepository missionRepository;
    ResourceTimeLockRepository resourceTimeLockRepository;
    UserScheduleRepository userScheduleRepository;

    @Override
    @Transactional(readOnly = true)
    public List<AvailableOperatorResponse> getAvailableOperators() {
        return getAvailableOperators(null);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AvailableOperatorResponse> getAvailableOperators(String missionId) {
        Mission mission = null;
        if (missionId != null && !missionId.isBlank()) {
            mission = missionRepository.findById(missionId)
                    .orElseThrow(() -> new ApiException(ErrorCode.MISSION_NOT_FOUND, "Mission not found: " + missionId));
        }
        Mission targetMission = mission;
        return userRepository.findAllByRole_CodeAndIsActiveTrueOrderByFullNameAsc(RoleCode.DRONE_OPERATOR)
                .stream()
                .filter(user -> targetMission == null || isAvailableForMission(user, targetMission))
                .map(user -> AvailableOperatorResponse.builder()
                        .id(user.getId())
                        .fullName(user.getFullName())
                        .email(user.getEmail())
                        .build())
                .toList();
    }

    private boolean isAvailableForMission(User user, Mission mission) {
        if (mission.getScheduledStartAt() == null || mission.getScheduledEndAt() == null) {
            return true;
        }
        return !hasLockConflict(user.getId(), mission)
                && !hasScheduleConflict(user.getId(), mission);
    }

    private boolean hasLockConflict(String staffId, Mission mission) {
        Instant start = mission.getScheduledStartAt();
        Instant end = mission.getScheduledEndAt();
        for (ResourceTimeLock lock : resourceTimeLockRepository.findByResourceId(staffId)) {
            if (isReleasedLock(lock)) {
                continue;
            }
            if (lock.getMission() != null && mission.getId().equals(lock.getMission().getId())) {
                continue;
            }
            if (overlaps(lock.getStartTime(), lock.getEndTime(), start, end)) {
                return true;
            }
        }
        return false;
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

    private boolean hasScheduleConflict(String staffId, Mission mission) {
        Instant start = mission.getScheduledStartAt();
        Instant end = mission.getScheduledEndAt();
        for (UserSchedule schedule : userScheduleRepository.findByStaffId(staffId)) {
            if (schedule.getStatus() == UserScheduleStatus.CANCELLED
                    || schedule.getStatus() == UserScheduleStatus.COMPLETED) {
                continue;
            }
            if (mission.getId().equals(schedule.getReferenceId())) {
                continue;
            }
            if (overlaps(schedule.getStartTime(), schedule.getEndTime(), start, end)) {
                return true;
            }
        }
        return false;
    }

    private boolean overlaps(Instant startA, Instant endA, Instant startB, Instant endB) {
        if (startA == null || endA == null || startB == null || endB == null) {
            return false;
        }
        return startA.isBefore(endB) && endA.isAfter(startB);
    }
}
