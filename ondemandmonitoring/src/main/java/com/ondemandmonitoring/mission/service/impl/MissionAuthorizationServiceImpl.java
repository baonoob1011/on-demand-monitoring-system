package com.ondemandmonitoring.mission.service.impl;

import com.ondemandmonitoring.drone.repository.PersistedPreflightCheckRepository;
import com.ondemandmonitoring.mission.repository.MissionOperatorAssignmentRepository;
import com.ondemandmonitoring.mission.repository.MissionRepository;
import com.ondemandmonitoring.mission.enums.MissionStatus;
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

    MissionOperatorAssignmentRepository missionOperatorAssignmentRepository;
    MissionRepository missionRepository;
    PersistedPreflightCheckRepository persistedPreflightCheckRepository;
    AuthenticatedUserResolver authenticatedUserResolver;

    @Override
    @Transactional(readOnly = true)
    public boolean isAssignedOperator(String missionId) {
        User currentUser = authenticatedUserResolver.getCurrentUser();
        boolean currentAssignment = missionOperatorAssignmentRepository.findByMissionIdAndIsCurrentTrue(missionId)
                .map(assignment -> currentUser.getId().toString().equals(assignment.getOperatorId()))
                .orElse(false);
        if (currentAssignment) return true;
        return missionRepository.findById(missionId)
                .filter(mission -> mission.getStatus() == MissionStatus.COMPLETED
                        || mission.getStatus() == MissionStatus.FAILED
                        || mission.getStatus() == MissionStatus.CANCELLED)
                .map(mission -> missionOperatorAssignmentRepository.existsByMissionIdAndOperatorId(
                        missionId, currentUser.getId().toString()))
                .orElse(false);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isAssignedOperatorForPreflight(String preflightId) {
        return persistedPreflightCheckRepository.findById(preflightId)
                .map(run -> isAssignedOperator(run.getMission().getId()))
                .orElse(false);
    }
}
