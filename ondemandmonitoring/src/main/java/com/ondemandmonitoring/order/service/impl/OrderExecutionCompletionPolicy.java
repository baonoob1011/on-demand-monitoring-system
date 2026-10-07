package com.ondemandmonitoring.order.service.impl;

import com.ondemandmonitoring.common.exception.ApiException;
import com.ondemandmonitoring.common.exception.ErrorCode;
import com.ondemandmonitoring.mission.enums.MissionResultApprovalStatus;
import com.ondemandmonitoring.mission.enums.MissionResultStatus;
import com.ondemandmonitoring.mission.enums.MissionStatus;
import com.ondemandmonitoring.mission.repository.MissionRepository;
import com.ondemandmonitoring.mission.repository.MissionResultRepository;
import com.ondemandmonitoring.mission.service.IMissionChecklistExecutionService;
import com.ondemandmonitoring.mission.service.impl.ExecutionPlanCoverageService;
import com.ondemandmonitoring.order.enums.OrderStatus;
import com.ondemandmonitoring.order.enums.ExecutionPlanStatus;
import com.ondemandmonitoring.order.repository.OrderRepository;
import com.ondemandmonitoring.order.service.IOrderExecutionCompletionPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** The sole writer of Order COMPLETED; caller must use the locked aggregate in the same transaction. */
@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class OrderExecutionCompletionPolicy implements IOrderExecutionCompletionPolicy {
    private final OrderRepository orders;
    private final MissionRepository missions;
    private final MissionResultRepository results;
    private final IMissionChecklistExecutionService checklist;

    @Override
    public LockedExecution lockAggregate(String orderId) {
        var order = orders.findByIdForUpdate(orderId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));
        // Read scalar IDs before locking, not potentially stale managed Mission entities.
        var children = missions.findIdsByOrderIdOrderById(orderId).stream().sorted()
                .map(id -> missions.findByIdForUpdate(id)
                        .orElseThrow(() -> new ApiException(ErrorCode.MISSION_NOT_FOUND))).toList();
        return new LockedExecution(order, children);
    }

    @Override
    public boolean completeIfReady(LockedExecution execution) {
        var order = execution.order();
        // Legacy runtime compatibility must never imply aggregate plan finalization.
        if (order.getExecutionPlanStatus() != ExecutionPlanStatus.FINALIZED) return false;
        if (order.getOrderStatus() != OrderStatus.APPROVED && order.getOrderStatus() != OrderStatus.IN_PROGRESS
                && order.getOrderStatus() != OrderStatus.COMPLETED) return false;
        var effective = execution.missions().stream().filter(ExecutionPlanCoverageService::effective).toList();
        if (effective.isEmpty()) return false;
        for (var mission : effective) {
            if (mission.getStatus() != MissionStatus.COMPLETED) return false;
            var result = results.findByMissionId(mission.getId());
            if (result.isEmpty() || result.get().getApprovalStatus() != MissionResultApprovalStatus.APPROVED
                    || result.get().getStatus() == MissionResultStatus.FAILED) return false;
            // Expected incompleteness is a boolean, not an exception caught from a transactional
            // proxy (which would mark the approval transaction rollback-only).
            // NON_COMPLIANT and justified UNABLE_TO_VERIFY retain their existing semantics.
            if (!checklist.isReadyForFinalApproval(mission)) return false;
        }
        if (order.getOrderStatus() != OrderStatus.COMPLETED) {
            order.setOrderStatus(OrderStatus.COMPLETED);
            orders.saveAndFlush(order);
        }
        return true;
    }
}
