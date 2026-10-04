package com.ondemandmonitoring.mission.service.impl;

import com.ondemandmonitoring.common.exception.ApiException;
import com.ondemandmonitoring.common.exception.ErrorCode;
import com.ondemandmonitoring.mission.domain.Mission;
import com.ondemandmonitoring.mission.domain.MissionChecklistExecution;
import com.ondemandmonitoring.mission.dto.request.ChecklistExecutionUpdateRequest;
import com.ondemandmonitoring.mission.dto.response.ChecklistExecutionResponse;
import com.ondemandmonitoring.mission.dto.response.MissionChecklistResponse;
import com.ondemandmonitoring.mission.enums.ChecklistExecutionStatus;
import com.ondemandmonitoring.mission.enums.MissionResultApprovalStatus;
import com.ondemandmonitoring.mission.mapper.MissionChecklistExecutionMapper;
import com.ondemandmonitoring.mission.repository.*;
import com.ondemandmonitoring.mission.service.IMissionAuthorizationService;
import com.ondemandmonitoring.mission.service.IMissionChecklistExecutionService;
import com.ondemandmonitoring.order.domain.OrderChecklistItem;
import com.ondemandmonitoring.order.repository.OrderChecklistItemRepository;
import com.ondemandmonitoring.user.service.AuthenticatedUserResolver;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MissionChecklistExecutionServiceImpl implements IMissionChecklistExecutionService {
    private final MissionRepository missions;
    private final OrderChecklistItemRepository items;
    private final MissionChecklistExecutionRepository executions;
    private final MissionResultRepository results;
    private final IMissionAuthorizationService authorization;
    private final AuthenticatedUserResolver currentUser;
    private final MissionChecklistExecutionMapper mapper;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void initialize(Mission mission) {
        List<MissionChecklistExecution> created = snapshotItems(mission).stream().map(item -> {
            requireSameOrder(mission, item);
            MissionChecklistExecution execution = new MissionChecklistExecution();
            execution.setMissionId(mission.getId());
            execution.setOrderId(mission.getOrder().getId());
            execution.setOrderChecklistItemId(item.getId());
            execution.setMission(mission);
            execution.setOrderChecklistItem(item);
            return execution;
        }).toList();
        try {
            executions.saveAllAndFlush(created);
        } catch (org.springframework.dao.DataIntegrityViolationException | org.hibernate.exception.ConstraintViolationException exception) {
            throw new ApiException(ErrorCode.CHECKLIST_EXECUTION_INTEGRITY_INVALID);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public MissionChecklistResponse getByMissionId(String missionId) {
        Mission mission = requireMission(missionId, false);
        if (!authorization.canViewMission(missionId)) throw new ApiException(ErrorCode.ACCESS_DENIED);
        return new MissionChecklistResponse(missionId, mission.getOrder().getChecklistSnapshotAt() == null,
                isReadyForSubmission(mission), executions.findOrderedByMissionId(missionId).stream()
                        .map(mapper::toResponse).toList());
    }

    @Override
    @Transactional
    public ChecklistExecutionResponse update(String missionId, String executionId,
            ChecklistExecutionUpdateRequest request) {
        // All execution mutations and result submissions/reviews lock the same Mission first.
        // This prevents a submit from racing an execution update across different rows.
        Mission mission = requireMission(missionId, true);
        if (!authorization.canExecuteMonitoringChecklist(missionId)) throw new ApiException(ErrorCode.ACCESS_DENIED);
        var result = results.findByMissionId(missionId);
        boolean rejected = result.map(r -> r.getApprovalStatus() == MissionResultApprovalStatus.REJECTED).orElse(false);
        if (result.map(r -> r.getApprovalStatus() == MissionResultApprovalStatus.PENDING_MANAGER_APPROVAL
                || r.getApprovalStatus() == MissionResultApprovalStatus.APPROVED).orElse(false)) {
            throw new ApiException(ErrorCode.CHECKLIST_EXECUTION_LOCKED);
        }
        MissionChecklistExecution execution = executions.findById(executionId)
                .orElseThrow(() -> new ApiException(ErrorCode.CHECKLIST_EXECUTION_NOT_FOUND));
        if (!missionId.equals(execution.getMissionId()))
            throw new ApiException(ErrorCode.CHECKLIST_EXECUTION_PARENT_MISMATCH);
        requireSameOrder(mission, execution.getOrderChecklistItem());
        if (!Objects.equals(execution.getOrderId(), mission.getOrder().getId()))
            throw new ApiException(ErrorCode.CHECKLIST_EXECUTION_INTEGRITY_INVALID);
        if (request == null || request.getExpectedVersion() == null || request.getExpectedVersion() < 0
                || request.getExecutionStatus() == null || request.getAssessmentStatus() == null)
            throw new ApiException(ErrorCode.INVALID_REQUEST);
        if (!request.getExpectedVersion().equals(execution.getVersion()))
            throw new ApiException(ErrorCode.CONCURRENT_UPDATE);
        ChecklistExecutionStatus next = request.getExecutionStatus();
        ChecklistExecutionStatus previous = execution.getExecutionStatus();
        if (!validTransition(previous, next, rejected))
            throw new ApiException(ErrorCode.CHECKLIST_EXECUTION_TRANSITION_INVALID);
        String reason = trimmed(request.getUnableToVerifyReason(), 1000);
        if (next == ChecklistExecutionStatus.UNABLE_TO_VERIFY && reason == null)
            throw new ApiException(ErrorCode.INVALID_REQUEST, "Unable to verify requires a non-blank reason");
        String observation = trimmed(request.getObservation(), 2000);
        Instant now = Instant.now();
        if (next == ChecklistExecutionStatus.PENDING) execution.setStartedAt(null);
        else if (execution.getStartedAt() == null) execution.setStartedAt(now);
        execution.setCompletedAt(next.isTerminal()
                ? (previous == next ? execution.getCompletedAt() : now) : null);
        execution.setExecutionStatus(next);
        execution.setAssessmentStatus(request.getAssessmentStatus());
        execution.setObservation(observation);
        execution.setUnableToVerifyReason(next == ChecklistExecutionStatus.UNABLE_TO_VERIFY ? reason : null);
        execution.setLastModifiedBy(currentUser.getCurrentUserId());
        // A repeated command still records an audit event and consumes its expected version.
        execution.setUpdatedAt(now);
        try {
            return mapper.toResponse(executions.saveAndFlush(execution));
        } catch (org.springframework.dao.DataIntegrityViolationException | org.hibernate.exception.ConstraintViolationException exception) {
            throw new ApiException(ErrorCode.CHECKLIST_EXECUTION_INTEGRITY_INVALID);
        }
    }

    private boolean validTransition(ChecklistExecutionStatus previous, ChecklistExecutionStatus next, boolean rejected) {
        if (previous == next) return true;
        if (previous.isTerminal()) return rejected;
        if (previous == ChecklistExecutionStatus.PENDING)
            return next == ChecklistExecutionStatus.IN_PROGRESS || next.isTerminal();
        return next.isTerminal();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isReadyForSubmission(Mission mission) {
        List<OrderChecklistItem> expected = snapshotItems(mission);
        List<MissionChecklistExecution> actual = executions.findOrderedByMissionId(mission.getId());
        Set<String> expectedIds = expected.stream().map(OrderChecklistItem::getId).collect(Collectors.toSet());
        if (expectedIds.size() != expected.size() || actual.size() != expected.size()) return false;
        Set<String> actualIds = actual.stream().map(MissionChecklistExecution::getOrderChecklistItemId).collect(Collectors.toSet());
        return actualIds.equals(expectedIds) && actual.stream().allMatch(execution ->
                mission.getOrder().getId().equals(execution.getOrderId())
                && execution.getExecutionStatus().isTerminal()
                && (execution.getExecutionStatus() != ChecklistExecutionStatus.UNABLE_TO_VERIFY
                    || execution.getUnableToVerifyReason() != null && !execution.getUnableToVerifyReason().isBlank()));
        // Empty historical snapshot is satisfied, including legacy orders; never infer current templates.
    }

    @Override
    @Transactional(readOnly = true)
    public void requireReadyForSubmission(Mission mission) {
        if (!isReadyForSubmission(mission)) throw new ApiException(ErrorCode.CHECKLIST_NOT_READY);
    }

    private List<OrderChecklistItem> snapshotItems(Mission mission) {
        return items.findAllByOrderIdInOrderByDisplayOrderAscIdAsc(List.of(mission.getOrder().getId()));
    }

    private void requireSameOrder(Mission mission, OrderChecklistItem item) {
        if (item == null || !Objects.equals(mission.getOrder().getId(), item.getOrder().getId()))
            throw new ApiException(ErrorCode.CHECKLIST_EXECUTION_INTEGRITY_INVALID);
    }

    private Mission requireMission(String id, boolean lock) {
        return (lock ? missions.findByIdForUpdate(id) : missions.findById(id))
                .orElseThrow(() -> new ApiException(ErrorCode.MISSION_NOT_FOUND));
    }

    private String trimmed(String value, int maxLength) {
        if (value == null) return null;
        if (value.length() > maxLength) throw new ApiException(ErrorCode.INVALID_REQUEST, "Checklist text is too long");
        return value.strip().isEmpty() ? null : value.strip();
    }
}
