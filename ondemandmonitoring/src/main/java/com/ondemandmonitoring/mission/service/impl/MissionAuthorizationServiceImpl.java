package com.ondemandmonitoring.mission.service.impl;

import com.ondemandmonitoring.devicecheck.repository.PersistedPreDeviceCheckRepository;
import com.ondemandmonitoring.mission.enums.MissionStaffRole;
import com.ondemandmonitoring.mission.repository.MissionRepository;
import com.ondemandmonitoring.mission.enums.MissionStatus;
import com.ondemandmonitoring.mission.repository.MissionStaffAssignmentRepository;
import com.ondemandmonitoring.mission.service.IMissionAuthorizationService;
import com.ondemandmonitoring.user.domain.User;
import com.ondemandmonitoring.user.service.AuthenticatedUserResolver;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service("missionAuthorizationService")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class MissionAuthorizationServiceImpl implements IMissionAuthorizationService {

        MissionStaffAssignmentRepository missionStaffAssignmentRepository;
        MissionRepository missionRepository;
        PersistedPreDeviceCheckRepository persistedPreDeviceCheckRepository;
        AuthenticatedUserResolver authenticatedUserResolver;

        @Override
        @Transactional(readOnly = true)
        public boolean isAssignedStaff(String missionIdOrOrderId) {
                User currentUser = authenticatedUserResolver.getCurrentUser();
                String missionId = missionRepository.findById(missionIdOrOrderId)
                                .or(() -> missionRepository.findByOrderId(missionIdOrOrderId))
                                .map(mission -> mission.getId())
                                .orElse(missionIdOrOrderId);
                boolean currentAssignment = missionStaffAssignmentRepository
                                .findByMissionIdAndStaffIdAndIsCurrentTrue(
                                                missionId,
                                                currentUser.getId().toString())
                                .isPresent();
                if (currentAssignment)
                        return true;
                return missionRepository.findById(missionId)
                                .filter(mission -> mission.getStatus() == MissionStatus.COMPLETED
                                                || mission.getStatus() == MissionStatus.FAILED
                                                || mission.getStatus() == MissionStatus.CANCELLED)
                                .map(mission -> missionStaffAssignmentRepository.existsByMissionIdAndStaffId(
                                                missionId, currentUser.getId().toString()))
                                .orElse(false);
        }

        @Override
        @Transactional(readOnly = true)
        public boolean isAssignedOperator(String missionIdOrOrderId) {
                User currentUser = authenticatedUserResolver.getCurrentUser();
                String missionId = missionRepository.findById(missionIdOrOrderId)
                                .or(() -> missionRepository.findByOrderId(missionIdOrOrderId))
                                .map(mission -> mission.getId())
                                .orElse(missionIdOrOrderId);
                return missionStaffAssignmentRepository
                                .findAllByMissionIdAndAssignedRoleAndIsCurrentTrue(
                                                missionId,
                                                MissionStaffRole.OPERATOR)
                                .stream()
                                .anyMatch(assignment -> assignment.getStaff() != null
                                                && currentUser.getId().toString()
                                                                .equals(assignment.getStaff().getId()));
        }

        @Override
        @Transactional(readOnly = true)
        public boolean isAssignedStaffForPreDeviceCheck(String preDeviceCheckId) {
                return persistedPreDeviceCheckRepository.findById(preDeviceCheckId)
                                .map(run -> isAssignedStaff(run.getMission().getId()))
                                .orElse(false);
        }
}

