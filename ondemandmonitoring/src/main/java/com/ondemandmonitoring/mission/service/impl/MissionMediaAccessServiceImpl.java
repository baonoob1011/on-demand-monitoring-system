package com.ondemandmonitoring.mission.service.impl;

import com.ondemandmonitoring.common.exception.ApiException;
import com.ondemandmonitoring.common.exception.ErrorCode;
import com.ondemandmonitoring.mission.domain.Mission;
import com.ondemandmonitoring.mission.dto.response.MissionMediaContext;
import com.ondemandmonitoring.mission.enums.MissionStatus;
import com.ondemandmonitoring.mission.repository.MissionRepository;
import com.ondemandmonitoring.mission.repository.MissionDeviceAssignmentRepository;
import com.ondemandmonitoring.mission.repository.MissionStaffAssignmentRepository;
import com.ondemandmonitoring.mission.service.IMissionMediaAccessService;
import com.ondemandmonitoring.user.service.AuthenticatedUserResolver;
import java.util.List;
import java.util.Set;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class MissionMediaAccessServiceImpl implements IMissionMediaAccessService {
    static Set<MissionStatus> CAPTURE_STATUSES = Set.of(
            MissionStatus.IN_FLIGHT, MissionStatus.IN_PROGRESS, MissionStatus.RETURNING,
            MissionStatus.POSTFLIGHT_CHECKING, MissionStatus.COMPLETED);

    MissionRepository missions;
    MissionDeviceAssignmentRepository deviceAssignments;
    MissionStaffAssignmentRepository staffAssignments;
    AuthenticatedUserResolver currentUser;

    @Override
    public MissionMediaContext authorizeOperator(String identifier) {
        Mission mission = requireMission(identifier);
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            throw new ApiException(ErrorCode.UNAUTHORIZED);
        }
        boolean privileged = auth.getAuthorities().stream().anyMatch(authority ->
                "ROLE_ADMIN".equals(authority.getAuthority())
                        || "ROLE_SYSTEM_OPERATOR".equals(authority.getAuthority()));
        String userId = currentUser.getCurrentUserId();
        boolean assigned = !staffAssignments
                .findAllByMissionIdAndStaffIdAndIsCurrentTrueOrderByAssignedAtDesc(mission.getId(), userId)
                .isEmpty();
        if (!assigned && mission.getStatus() == MissionStatus.COMPLETED) {
            assigned = staffAssignments.findByMissionId(mission.getId()).stream()
                    .anyMatch(entry -> userId.equals(entry.getStaff().getId()));
        }
        if (!privileged && !assigned) {
            throw new ApiException(ErrorCode.ACCESS_DENIED, "Operator is not assigned to mission");
        }
        return new MissionMediaContext(mission.getId(), CAPTURE_STATUSES.contains(mission.getStatus()), mission.getStatus());
    }

    @Override
    public void requireAssignedDevice(String missionId, String deviceId) {
        // Independently authorize the public boundary, even when called outside media.
        authorizeOperator(missionId);
        Mission mission = requireMission(missionId);
        boolean assigned = deviceAssignments.findAllByMissionIdAndIsCurrentTrueOrderByCreatedAtDesc(mission.getId())
                .stream()
                .anyMatch(entry -> entry.getDevice() != null && deviceId.equals(entry.getDevice().getId()));
        if (!assigned && mission.getStatus() == MissionStatus.COMPLETED) {
            assigned = deviceAssignments.findByMissionId(mission.getId()).stream()
                    .anyMatch(entry -> deviceId.equals(entry.getDevice().getId()));
        }
        if (!assigned) {
            throw new ApiException(ErrorCode.ACCESS_DENIED, "Device is not assigned to mission");
        }
    }

    @Override
    public String authorizeCustomer(String identifier) {
        Mission mission = requireMission(identifier);
        String userId = currentUser.getCurrentUserId();
        if (mission.getOrder() == null || mission.getOrder().getCustomer() == null
                || !userId.equals(mission.getOrder().getCustomer().getId())) {
            throw new ApiException(ErrorCode.ACCESS_DENIED);
        }
        return mission.getId();
    }

    @Override
    public List<String> ownCustomerMissionIds() {
        return missions.findByOrder_Customer_Id(currentUser.getCurrentUserId()).stream()
                .map(Mission::getId).toList();
    }

    private Mission requireMission(String identifier) {
        return missions.findById(identifier).or(() -> missions.findByMissionCode(identifier))
                .orElseThrow(() -> new ApiException(ErrorCode.MISSION_NOT_FOUND));
    }
}
