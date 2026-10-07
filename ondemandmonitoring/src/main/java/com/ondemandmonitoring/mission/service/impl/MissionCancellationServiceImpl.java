package com.ondemandmonitoring.mission.service.impl;

import com.ondemandmonitoring.common.exception.*;
import com.ondemandmonitoring.mission.dto.request.MissionCancelRequest;
import com.ondemandmonitoring.mission.dto.response.MissionCancellationResponse;
import com.ondemandmonitoring.mission.enums.*;
import com.ondemandmonitoring.mission.repository.*;
import com.ondemandmonitoring.mission.service.IMissionCancellationService;
import com.ondemandmonitoring.order.enums.*;
import com.ondemandmonitoring.order.repository.OrderRepository;
import com.ondemandmonitoring.user.service.AuthenticatedUserResolver;
import java.time.Instant;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
public class MissionCancellationServiceImpl implements IMissionCancellationService {
    private static final Set<MissionStatus> SAFE_STATES = Set.of(MissionStatus.CREATED,
            MissionStatus.RESOURCE_ASSIGNING, MissionStatus.WAITING_CREW_CONFIRMATION,
            MissionStatus.WAITING_OPERATOR_ACCEPTANCE, MissionStatus.SCHEDULED,
            MissionStatus.FAILED_PREFLIGHT, MissionStatus.PENDING_APPROVAL, MissionStatus.FAILED, MissionStatus.COMPLETED);
    private final OrderRepository orders;
    private final MissionRepository missions;
    private final MissionResultRepository results;
    private final AuthenticatedUserResolver actor;

    @Override
    @Transactional
    public MissionCancellationResponse cancel(String missionId, MissionCancelRequest request) {
        if (request == null || request.expectedPlanRevision() == null || request.expectedPlanRevision() < 0
                || request.reason() == null || request.reason().isBlank() || request.reason().length() > 1000)
            throw new ApiException(ErrorCode.INVALID_REQUEST);
        String orderId = missions.findOrderIdByMissionId(missionId)
                .orElseThrow(() -> new ApiException(ErrorCode.MISSION_NOT_FOUND));
        var order = orders.findByIdForUpdate(orderId).orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));
        var mission = missions.findByIdForUpdate(missionId).orElseThrow(() -> new ApiException(ErrorCode.MISSION_NOT_FOUND));
        if (!orderId.equals(mission.getOrder().getId())) throw new ApiException(ErrorCode.MISSION_CANCELLATION_NOT_ALLOWED);
        if (mission.getStatus() == MissionStatus.CANCELLED || mission.getCancellationAt() != null)
            throw new ApiException(ErrorCode.MISSION_ALREADY_CANCELLED);
        if (order.getOrderStatus() != OrderStatus.APPROVED && order.getOrderStatus() != OrderStatus.IN_PROGRESS)
            throw new ApiException(ErrorCode.MISSION_CANCELLATION_NOT_ALLOWED);
        if (order.getExecutionPlanStatus() != ExecutionPlanStatus.DRAFT) throw new ApiException(ErrorCode.EXECUTION_PLAN_NOT_DRAFT);
        if (order.getExecutionPlanRevision() != request.expectedPlanRevision() || order.getExecutionPlanRevision() == Long.MAX_VALUE)
            throw new ApiException(ErrorCode.EXECUTION_PLAN_REVISION_CONFLICT);
        if (mission.getCreationIdempotencyKey() == null) throw new ApiException(ErrorCode.MULTI_MISSION_LEGACY_ORDER_NOT_MIGRATED);
        if (results.existsByMissionIdAndApprovalStatus(missionId, MissionResultApprovalStatus.APPROVED))
            throw new ApiException(ErrorCode.MISSION_RESULT_ALREADY_APPROVED);
        if ((!SAFE_STATES.contains(mission.getStatus()) && !(mission.getStatus() == MissionStatus.PENDING_REVIEW
                && mission.getSafeStopStatus() == MissionSafeStopStatus.READY_FOR_CANCELLATION))
                || (mission.getSafeStopRequestedAt() != null && mission.getSafeStopStatus() != MissionSafeStopStatus.READY_FOR_CANCELLATION)
                || missions.hasUnsafeCancellationResources(missionId))
            throw new ApiException(ErrorCode.MISSION_CANCELLATION_REQUIRES_SAFE_STOP);
        mission.setStatus(MissionStatus.CANCELLED);
        mission.setCancellationAt(Instant.now()); mission.setCancelledBy(actor.getCurrentUserId());
        mission.setCancellationReason(request.reason().strip());
        order.setExecutionPlanRevision(order.getExecutionPlanRevision() + 1);
        missions.saveAndFlush(mission); orders.saveAndFlush(order);
        return new MissionCancellationResponse(missionId, mission.getStatus(), order.getExecutionPlanRevision(),
                mission.getCancellationAt(), mission.getCancelledBy(), mission.getCancellationReason());
    }
}
