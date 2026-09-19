package com.ondemandmonitoring.mission.service.impl;

import com.ondemandmonitoring.common.exception.ApiException;
import com.ondemandmonitoring.common.exception.ErrorCode;
import com.ondemandmonitoring.mission.domain.Mission;
import com.ondemandmonitoring.mission.domain.MissionDroneAssignment;
import com.ondemandmonitoring.mission.domain.MissionOperatorAssignment;
import com.ondemandmonitoring.mission.dto.response.MissionControlContextResponse;
import com.ondemandmonitoring.mission.enums.MissionStatus;
import com.ondemandmonitoring.mission.repository.MissionRepository;
import com.ondemandmonitoring.mission.repository.MissionDroneAssignmentRepository;
import com.ondemandmonitoring.mission.repository.MissionOperatorAssignmentRepository;
import com.ondemandmonitoring.mission.service.IMissionControlAuthorizationService;
import com.ondemandmonitoring.user.service.IUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class MissionControlAuthorizationService implements IMissionControlAuthorizationService {

    private static final Set<MissionStatus> CONTROL_STATUSES = EnumSet.of(
            MissionStatus.CONNECTED,
            MissionStatus.PREFLIGHT_CHECKING,
            MissionStatus.READY_TO_FLY,
            MissionStatus.IN_FLIGHT,
            MissionStatus.IN_PROGRESS,
            MissionStatus.RETURNING);
    private static final Set<MissionStatus> CAPTURE_STATUSES = EnumSet.of(
            MissionStatus.IN_FLIGHT,
            MissionStatus.IN_PROGRESS);
    private static final Set<MissionStatus> UPLOAD_STATUSES = EnumSet.of(
            MissionStatus.IN_FLIGHT,
            MissionStatus.IN_PROGRESS,
            MissionStatus.RETURNING);

    private final MissionRepository missionRepository;
    private final MissionDroneAssignmentRepository missionDroneAssignmentRepository;
    private final MissionOperatorAssignmentRepository missionOperatorAssignmentRepository;
    private final IUserService userService;

    @Override
    @Transactional(readOnly = true)
    public MissionControlContextResponse authorize(String missionId, String droneId) {
        Mission mission = missionRepository.findById(missionId)
                .or(() -> missionRepository.findByMissionCode(missionId))
                .orElseThrow(() -> new ApiException(ErrorCode.MISSION_NOT_FOUND));
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ApiException(ErrorCode.UNAUTHORIZED);
        }

        boolean privileged = authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_ADMIN")
                        || authority.getAuthority().equals("ROLE_SYSTEM_OPERATOR"));
        String actorId = privileged
                ? null
                : userService.findByCognitoSub(authentication.getName()).getId().toString();
        String assignedOperatorId = currentOperatorId(mission);
        if (!privileged && (assignedOperatorId == null
                || !assignedOperatorId.equals(actorId))) {
            throw new ApiException(ErrorCode.ACCESS_DENIED, "Operator is not assigned to this mission");
        }
        String assignedDrone = currentDroneCode(mission);
        if (assignedDrone == null) {
            throw new ApiException(ErrorCode.DRONE_NOT_AVAILABLE, "Mission has no assigned drone");
        }

        if (droneId != null && !droneId.isBlank() && !assignedDrone.equals(droneId)) {
            throw new ApiException(ErrorCode.ACCESS_DENIED, "Drone is not assigned to this mission");
        }

        MissionStatus status = mission.getStatus();
        return new MissionControlContextResponse(
                mission.getId().toString(),
                mission.getMissionCode(),
                assignedDrone,
                assignedOperatorId,
                status.name(),
                CONTROL_STATUSES.contains(status),
                CAPTURE_STATUSES.contains(status),
                UPLOAD_STATUSES.contains(status));
    }

    private String currentOperatorId(Mission mission) {
        return missionOperatorAssignmentRepository.findByMissionIdAndIsCurrentTrue(mission.getId())
                .map(MissionOperatorAssignment::getOperatorId)
                .orElse(null);
    }

    private String currentDroneCode(Mission mission) {
        return missionDroneAssignmentRepository.findByMissionIdAndIsCurrentTrue(mission.getId())
                .map(MissionDroneAssignment::getDrone)
                .map(drone -> drone.getDroneCode())
                .orElse(null);
    }
}
