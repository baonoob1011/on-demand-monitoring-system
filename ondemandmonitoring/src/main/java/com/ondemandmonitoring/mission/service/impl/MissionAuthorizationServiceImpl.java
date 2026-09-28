package com.ondemandmonitoring.mission.service.impl;

import com.ondemandmonitoring.drone.repository.PersistedPreflightCheckRepository;
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
        PersistedPreflightCheckRepository persistedPreflightCheckRepository;
        AuthenticatedUserResolver authenticatedUserResolver;

        @Override
        @Transactional(readOnly = true)
        public boolean isAssignedStaff(String missionId) {
                User currentUser = authenticatedUserResolver.getCurrentUser();
                boolean currentAssignment = missionStaffAssignmentRepository
                                .findByMissionIdAndIsCurrentTrue(missionId)
                                .map(assignment -> currentUser.getId().toString().equals(assignment.getStaff().getId()))
                                .orElse(false);
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
        public boolean isAssignedStaffForPreflight(String preflightId) {
                return persistedPreflightCheckRepository.findById(preflightId)
                                .map(run -> isAssignedStaff(run.getMission().getId()))
                                .orElse(false);
        }
}
