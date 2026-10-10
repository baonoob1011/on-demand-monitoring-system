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
    private final com.ondemandmonitoring.mission.repository.MissionResultRepository results;
    private final com.ondemandmonitoring.mission.service.IMissionChecklistScopeService checklistScope;

    @Override
    public boolean canAttachChecklistEvidence(String missionId) {
        return findRuntimeMissionById(missionId).filter(m -> m.getStatus() == MissionStatus.PENDING_REVIEW || m.getStatus() == MissionStatus.COMPLETED)
                .filter(m -> results.findByMissionId(m.getId()).map(r -> r.getApprovalStatus() != com.ondemandmonitoring.mission.enums.MissionResultApprovalStatus.APPROVED
                        && r.getApprovalStatus() != com.ondemandmonitoring.mission.enums.MissionResultApprovalStatus.PENDING_MANAGER_APPROVAL).orElse(true))
                .map(m -> canExecuteMonitoringChecklist(m.getId()) || canUploadMissionMedia(m.getId())).orElse(false);
    }

    @Override
    public boolean canDetachChecklistEvidence(String missionId) { return canAttachChecklistEvidence(missionId); }

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
    public boolean canViewMission(String missionId) {
        Optional<Mission> mission = findMissionById(missionId);
        if (mission.isEmpty()) return false;
        if (canManageMissions()) return true;
        User user = currentUser.getCurrentUser();
        if (!hasRole(user, RoleCode.STAFF)) return false;
        if (!ownAssignments(mission.get().getId(), user).isEmpty()) return true;
        return assignments.existsByMissionIdAndStaffId(mission.get().getId(), user.getId());
    }

    @Override
    public boolean canRespondToMission(String missionId, String staffId) {
        User user = currentUser.getCurrentUser();
        if (!hasRole(user, RoleCode.STAFF) || !user.getId().equals(staffId)) return false;
        return findRuntimeMissionById(missionId).filter(mission ->
                mission.getStatus() == MissionStatus.WAITING_CREW_CONFIRMATION
                        || mission.getStatus() == MissionStatus.WAITING_OPERATOR_ACCEPTANCE)
                .map(mission -> ownAssignments(mission.getId(), user).stream()
                        .anyMatch(entry -> entry.getResponseStatus() == StaffResponseStatus.PENDING))
                .orElse(false);
    }

    @Override
    public boolean canRespondToMission(String missionId) {
        return canRespondToMission(missionId, currentUser.getCurrentUserId());
    }

    @Override
    public boolean canControlFlight(String missionId) {
        return normalOperationAllowed(missionId) && canPerform(missionId, MissionStaffRole.PILOT);
    }

    @Override
    public boolean canTerminateFlight(String missionId) {
        return findRuntimeMissionById(missionId)
                .filter(MissionRuntimeGate::hasActiveSafeStop)
                .filter(m -> m.getSafeStopRequestedAt() != null && m.getSafeStopConfirmedAt() == null
                        && m.getSafeStopCleanedAt() == null)
                .filter(m -> Set.of(MissionStatus.CONNECTED, MissionStatus.PREFLIGHT_CHECKING,
                        MissionStatus.READY_TO_FLY, MissionStatus.IN_FLIGHT, MissionStatus.IN_PROGRESS,
                        MissionStatus.RETURNING).contains(m.getStatus()))
                .map(m -> canPerform(m.getId(), MissionStaffRole.PILOT)).orElse(false);
    }

    private boolean normalOperationAllowed(String missionId) {
        return findRuntimeMissionById(missionId).filter(m -> !MissionRuntimeGate.hasActiveSafeStop(m)).isPresent();
    }

    @Override
    public boolean canPrepareSafeStopPostflight(String missionId) {
        // Close out a historical ground confirmation, not a flight or session recovery.
        return findRuntimeMissionById(missionId).filter(MissionRuntimeGate::allowsConfirmedGroundPostflight)
                .map(m -> canPerform(m.getId(), MissionStaffRole.PILOT)).orElse(false);
    }

    @Override
    public boolean canOperatePayload(String missionId) {
        return normalOperationAllowed(missionId) && canPerform(missionId, MissionStaffRole.OPERATOR);
    }

    @Override
    public boolean canExecuteMonitoringChecklist(String missionId) {
        User user = currentUser.getCurrentUser();
        if (!hasRole(user, RoleCode.STAFF)) return false;
        return findRuntimeMissionById(missionId).filter(mission -> Set.of(MissionStatus.IN_FLIGHT,
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
    public boolean canInspectDevice(String missionId) {
        return canPerform(missionId, MissionStaffRole.INSPECTOR);
    }

    @Override
    public boolean canMaintainDevice(String missionId) {
        return canPerform(missionId, MissionStaffRole.MAINTAINER);
    }

    @Override
    public boolean canViewMissionMedia(String missionId) {
        return canViewMission(missionId);
    }

    @Override
    public boolean canUploadMissionMedia(String missionId) {
        User user = currentUser.getCurrentUser();
        if (!hasRole(user, RoleCode.STAFF)) return false;
        return findRuntimeMissionById(missionId)
                .filter(mission -> mission.getStatus() == MissionStatus.PENDING_REVIEW
                        || mission.getStatus() == MissionStatus.COMPLETED)
                .map(mission -> hasEligibleInspectorAssignment(mission, user))
                .orElse(false);
    }

    @Override
    public boolean canCompleteMission(String missionId) {
        return findRuntimeMissionById(missionId)
                .filter(mission -> mission.getStatus() == MissionStatus.PENDING_REVIEW)
                .map(mission -> canUploadMissionMedia(mission.getId())).orElse(false);
    }

    @Override
    public boolean canSubmitMissionResult(String missionId) {
        // Actor/status capability only; submission also validates readiness, completion and result locks.
        // Reuse the monitoring actor policy, including Operator priority, Pilot fallback
        // and successful final-crew release. Inspector media/acceptance is independent.
        return findRuntimeMissionById(missionId)
                .filter(mission -> mission.getStatus() == MissionStatus.PENDING_REVIEW
                        || mission.getStatus() == MissionStatus.COMPLETED)
                .map(mission -> canExecuteMonitoringChecklist(mission.getId()))
                .orElse(false);
    }

    private boolean canPerform(String missionId, MissionStaffRole task) {
        User user = currentUser.getCurrentUser();
        if (!hasRole(user, RoleCode.STAFF)) return false;
        return findRuntimeMissionById(missionId).filter(mission -> !TERMINAL_STATUSES.contains(mission.getStatus()))
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

    private Optional<Mission> findMissionById(String missionId) {
        return missions.findById(missionId);
    }

    private Optional<Mission> findRuntimeMissionById(String missionId) {
        return findMissionById(missionId).filter(MissionRuntimeGate::isStructurallyFinalized).filter(checklistScope::isMaterialized);
    }

    @Override
    public boolean canViewMissionByCode(String missionCode) {
        return missions.findByMissionCode(missionCode)
                .map(mission -> canViewMission(mission.getId())).orElse(false);
    }

    private boolean hasRole(User user, RoleCode role) {
        return Boolean.TRUE.equals(user.getIsActive()) && user.getRole() != null
                && user.getRole().isActive() && user.getRole().getCode() == role;
    }
}
