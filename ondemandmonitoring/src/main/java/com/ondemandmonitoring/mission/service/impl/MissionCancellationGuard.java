package com.ondemandmonitoring.mission.service.impl;

import com.ondemandmonitoring.common.exception.ApiException;
import com.ondemandmonitoring.common.exception.ErrorCode;
import com.ondemandmonitoring.mission.domain.Mission;
import com.ondemandmonitoring.mission.enums.*;
import com.ondemandmonitoring.mission.repository.MissionRepository;
import com.ondemandmonitoring.mission.repository.MissionResultRepository;
import com.ondemandmonitoring.order.domain.Order;
import com.ondemandmonitoring.order.enums.*;
import java.util.Set;

/**
 * Shared existing cancellation preconditions for locked commands and
 * non-mutating capability reads.
 */
final class MissionCancellationGuard {
    // private static final Set<MissionStatus> SAFE_STATES =
    // Set.of(MissionStatus.PLANNING,
    // MissionStatus.WAITING_CREW_CONFIRMATION,
    // MissionStatus.WAITING_OPERATOR_ACCEPTANCE, MissionStatus.READY,
    // MissionStatus.SCHEDULED,
    // MissionStatus.FAILED_PREFLIGHT, MissionStatus.PENDING_APPROVAL,
    // MissionStatus.FAILED, MissionStatus.COMPLETED);

    private MissionCancellationGuard() {
    }

    static void requireAllowed(String orderId, Order order, Mission mission, long expectedRevision,
            MissionResultRepository results, MissionRepository missions) {
        if (!orderId.equals(mission.getOrder().getId()))
            throw new ApiException(ErrorCode.MISSION_CANCELLATION_NOT_ALLOWED);
        if (mission.getStatus() == MissionStatus.CANCELLED || mission.getCancellationAt() != null)
            throw new ApiException(ErrorCode.MISSION_ALREADY_CANCELLED);
        if (order.getOrderStatus() != OrderStatus.APPROVED && order.getOrderStatus() != OrderStatus.IN_PROGRESS)
            throw new ApiException(ErrorCode.MISSION_CANCELLATION_NOT_ALLOWED);
        if (order.getExecutionPlanStatus() != ExecutionPlanStatus.FINALIZED)
            throw new ApiException(ErrorCode.EXECUTION_PLAN_NOT_FINALIZED);
        if (order.getExecutionPlanRevision() != expectedRevision || order.getExecutionPlanRevision() == Long.MAX_VALUE)
            throw new ApiException(ErrorCode.EXECUTION_PLAN_REVISION_CONFLICT);
        if (results.existsByMissionIdAndApprovalStatus(mission.getId(), MissionResultApprovalStatus.APPROVED))
            throw new ApiException(ErrorCode.MISSION_RESULT_ALREADY_APPROVED);
        // if ((!SAFE_STATES.contains(mission.getStatus()) && !(mission.getStatus() ==
        // MissionStatus.PENDING_REVIEW
        // && mission.getSafeStopStatus() ==
        // MissionSafeStopStatus.READY_FOR_CANCELLATION))
        // || (mission.getSafeStopRequestedAt() != null
        // && mission.getSafeStopStatus() !=
        // MissionSafeStopStatus.READY_FOR_CANCELLATION)
        // || missions.hasUnsafeCancellationResources(mission.getId()))
        // throw new ApiException(ErrorCode.MISSION_CANCELLATION_REQUIRES_SAFE_STOP);
    }
}
