package com.ondemandmonitoring.mission.service.impl;

import com.ondemandmonitoring.mission.domain.Mission;
import com.ondemandmonitoring.mission.domain.MissionStaffAssignment;
import com.ondemandmonitoring.mission.enums.MissionStaffRole;
import com.ondemandmonitoring.mission.enums.MissionStatus;
import com.ondemandmonitoring.mission.enums.StaffResponseStatus;
import com.ondemandmonitoring.mission.repository.MissionRepository;
import com.ondemandmonitoring.mission.repository.MissionStaffAssignmentRepository;
import com.ondemandmonitoring.mission.service.IMissionAuthorizationService;
import com.ondemandmonitoring.role.domain.RoleCode;
import com.ondemandmonitoring.user.domain.User;
import com.ondemandmonitoring.user.service.AuthenticatedUserResolver;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service("missionAuthorizationService")
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MissionAuthorizationServiceImpl implements IMissionAuthorizationService {
    private static final Set<MissionStatus> TERMINAL_STATUSES = Set.of(
            MissionStatus.COMPLETED, MissionStatus.FAILED, MissionStatus.CANCELLED);
    private final MissionStaffAssignmentRepository assignments;
    private final MissionRepository missions;
    private final AuthenticatedUserResolver currentUser;

    @Override
    public boolean canManageMissions() {
        User user = currentUser.getCurrentUser();
        return hasRole(user, RoleCode.MANAGER) || hasRole(user, RoleCode.ADMIN);
    }

    @Override
    public boolean canViewStaffMissions(String staffId) {
        User user = currentUser.getCurrentUser();
        return canManageMissions() || (hasRole(user, RoleCode.STAFF) && user.getId().equals(staffId));
    }

    @Override
    public boolean canViewMission(String identifier) {
        Optional<Mission> mission = resolveMission(identifier);
        if (mission.isEmpty()) return false;
        if (canManageMissions()) return true;
        User user = currentUser.getCurrentUser();
        if (!hasRole(user, RoleCode.STAFF)) return false;
        if (!ownAssignments(mission.get().getId(), user).isEmpty()) return true;
        return TERMINAL_STATUSES.contains(mission.get().getStatus())
                && assignments.existsByMissionIdAndStaffId(mission.get().getId(), user.getId());
    }

    @Override
    public boolean canRespondToMission(String identifier, String staffId) {
        User user = currentUser.getCurrentUser();
        if (!hasRole(user, RoleCode.STAFF) || !user.getId().equals(staffId)) return false;
        return resolveMission(identifier).filter(mission ->
                mission.getStatus() == MissionStatus.WAITING_CREW_CONFIRMATION
                        || mission.getStatus() == MissionStatus.WAITING_OPERATOR_ACCEPTANCE)
                .map(mission -> ownAssignments(mission.getId(), user).stream()
                        .anyMatch(entry -> entry.getResponseStatus() == StaffResponseStatus.PENDING))
                .orElse(false);
    }

    @Override
    public boolean canRespondToMission(String identifier) {
        return canRespondToMission(identifier, currentUser.getCurrentUserId());
    }

    @Override
    public boolean canControlFlight(String identifier) {
        return canPerform(identifier, MissionStaffRole.PILOT);
    }

    @Override
    public boolean canOperatePayload(String identifier) {
        return canPerform(identifier, MissionStaffRole.OPERATOR);
    }

    @Override
    public boolean canExecuteMonitoringChecklist(String identifier) {
        User user = currentUser.getCurrentUser();
        if (!hasRole(user, RoleCode.STAFF)) return false;
        return resolveMission(identifier).filter(mission -> Set.of(MissionStatus.IN_FLIGHT,
                MissionStatus.IN_PROGRESS, MissionStatus.RETURNING, MissionStatus.POSTFLIGHT_CHECKING,
                MissionStatus.PENDING_REVIEW, MissionStatus.COMPLETED).contains(mission.getStatus())).map(mission -> {
                    boolean completed = mission.getStatus() == MissionStatus.COMPLETED;
                    // Operational completion releases current assignments. Only the final accepted crew
                    // released by successful completion can finish the business report afterwards.
                    List<MissionStaffAssignment> usable = assignments.findByMissionId(mission.getId()).stream()
                            .filter(entry -> entry.getStaff() != null && hasRole(entry.getStaff(), RoleCode.STAFF))
                            .filter(entry -> entry.getResponseStatus() == StaffResponseStatus.ACCEPTED)
                            .filter(entry -> completed
                                    ? entry.getReleasedAt() != null
                                        && "MISSION_COMPLETE".equals(entry.getReleaseReason())
                                    : Boolean.TRUE.equals(entry.getIsCurrent()) && entry.getReleasedAt() == null)
                            .toList();
                    MissionStaffRole required = usable.stream().anyMatch(entry ->
                            entry.getAssignedRole() == MissionStaffRole.OPERATOR)
                            ? MissionStaffRole.OPERATOR : MissionStaffRole.PILOT;
                    return usable.stream().anyMatch(entry -> entry.getAssignedRole() == required
                            && user.getId().equals(entry.getStaff().getId()));
                }).orElse(false);
    }

    @Override
    public boolean canInspectDevice(String identifier) {
        return canPerform(identifier, MissionStaffRole.INSPECTOR);
    }

    @Override
    public boolean canMaintainDevice(String identifier) {
        return canPerform(identifier, MissionStaffRole.MAINTAINER);
    }

    @Override
    public boolean canViewMissionMedia(String identifier) {
        return canViewMission(identifier);
    }

    @Override
    public boolean canUploadMissionMedia(String identifier) {
        User user = currentUser.getCurrentUser();
        if (!hasRole(user, RoleCode.STAFF)) return false;
        return resolveMission(identifier)
                .filter(mission -> mission.getStatus() == MissionStatus.PENDING_REVIEW
                        || mission.getStatus() == MissionStatus.COMPLETED)
                .map(mission -> hasEligibleInspectorAssignment(mission, user))
                .orElse(false);
    }

    @Override
    public boolean canCompleteMission(String identifier) {
        return resolveMission(identifier)
                .filter(mission -> mission.getStatus() == MissionStatus.PENDING_REVIEW)
                .map(mission -> canUploadMissionMedia(mission.getId())).orElse(false);
    }

    @Override
    public boolean canSubmitMissionResult(String identifier) {
        // Actor/status capability only; submission also validates readiness and result locks.
        return resolveMission(identifier)
                .filter(mission -> mission.getStatus() == MissionStatus.COMPLETED)
                .map(mission -> canExecuteMonitoringChecklist(mission.getId())).orElse(false);
    }

    private boolean canPerform(String identifier, MissionStaffRole task) {
        User user = currentUser.getCurrentUser();
        if (!hasRole(user, RoleCode.STAFF)) return false;
        return resolveMission(identifier).filter(mission -> !TERMINAL_STATUSES.contains(mission.getStatus()))
                .map(mission -> {
                    List<MissionStaffAssignment> crew = assignments.findAllByMissionIdAndIsCurrentTrue(mission.getId());
                    return crew.stream().anyMatch(entry -> entry.getStaff() != null
                            && user.getId().equals(entry.getStaff().getId())
                            && Boolean.TRUE.equals(entry.getIsCurrent()) && entry.getReleasedAt() == null
                            && entry.getAssignedRole() == task
                            && entry.getResponseStatus() == StaffResponseStatus.ACCEPTED);
                }).orElse(false);
    }

    private List<MissionStaffAssignment> ownAssignments(String missionId, User user) {
        return assignments.findAllByMissionIdAndStaffIdAndIsCurrentTrueOrderByAssignedAtDesc(missionId, user.getId())
                .stream().filter(entry -> Boolean.TRUE.equals(entry.getIsCurrent()) && entry.getReleasedAt() == null)
                .toList();
    }

    private boolean hasEligibleInspectorAssignment(Mission mission, User user) {
        return assignments.findByMissionId(mission.getId()).stream()
                .anyMatch(entry -> entry.getStaff() != null
                        && user.getId().equals(entry.getStaff().getId())
                        && hasRole(entry.getStaff(), RoleCode.STAFF)
                        && entry.getAssignedRole() == MissionStaffRole.INSPECTOR
                        && entry.getResponseStatus() == StaffResponseStatus.ACCEPTED
                        && Boolean.TRUE.equals(entry.getIsCurrent())
                        && (mission.getStatus() == MissionStatus.COMPLETED
                            ? entry.getReleasedAt() != null && "MISSION_COMPLETE".equals(entry.getReleaseReason())
                            : entry.getReleasedAt() == null));
    }

    private Optional<Mission> resolveMission(String identifier) {
        return missions.findById(identifier).or(() -> missions.findByOrderId(identifier))
                .or(() -> missions.findByMissionCode(identifier));
    }

    private boolean hasRole(User user, RoleCode role) {
        return Boolean.TRUE.equals(user.getIsActive()) && user.getRole() != null
                && user.getRole().isActive() && user.getRole().getCode() == role;
    }
}
