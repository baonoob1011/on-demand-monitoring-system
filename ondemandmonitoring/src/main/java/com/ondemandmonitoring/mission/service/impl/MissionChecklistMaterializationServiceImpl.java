package com.ondemandmonitoring.mission.service.impl;

import com.ondemandmonitoring.common.exception.*;
import com.ondemandmonitoring.mission.domain.*;
import com.ondemandmonitoring.mission.repository.*;
import com.ondemandmonitoring.mission.service.IMissionChecklistMaterializationService;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@RequiredArgsConstructor
public class MissionChecklistMaterializationServiceImpl implements IMissionChecklistMaterializationService {
    private final MissionChecklistAssignmentRepository assignments;
    private final MissionChecklistExecutionRepository executions;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void materialize(Mission mission) {
        var selected = assignments.findByMissionIdAndRemovedAtIsNullOrderByDisplayOrderAscIdAsc(mission.getId());
        var byAssignment = new HashMap<String, MissionChecklistAssignment>();
        var items = new HashSet<String>();
        for (var assignment : selected) {
            if (!ChecklistAssignmentIntegrity.matches(mission, assignment)
                    || byAssignment.put(assignment.getId(), assignment) != null
                    || !items.add(assignment.getOrderChecklistItemId())) invalid();
        }
        for (var execution : executions.findOrderedByMissionId(mission.getId())) {
            if (!ChecklistAssignmentIntegrity.matches(mission, execution)
                    || byAssignment.remove(execution.getMissionChecklistAssignmentId()) == null) invalid();
        }
        var created = new ArrayList<MissionChecklistExecution>();
        for (var assignment : selected) {
            if (!byAssignment.containsKey(assignment.getId())) continue;
            var execution = new MissionChecklistExecution();
            execution.setMissionId(mission.getId());
            execution.setOrderId(assignment.getOrderId());
            execution.setOrderChecklistItemId(assignment.getOrderChecklistItemId());
            execution.setMissionChecklistAssignmentId(assignment.getId());
            execution.setMission(mission);
            execution.setOrderChecklistItem(assignment.getOrderChecklistItem());
            execution.setMissionChecklistAssignment(assignment);
            created.add(execution);
        }
        try {
            executions.saveAllAndFlush(created);
        } catch (org.springframework.dao.DataIntegrityViolationException | org.hibernate.exception.ConstraintViolationException exception) {
            throw new ApiException(ErrorCode.CHECKLIST_EXECUTION_INTEGRITY_INVALID);
        }
    }

    private void invalid() { throw new ApiException(ErrorCode.CHECKLIST_EXECUTION_INTEGRITY_INVALID); }
}
