package com.ondemandmonitoring.mission.service.impl;

import com.ondemandmonitoring.common.exception.*;
import com.ondemandmonitoring.mission.domain.Mission;
import com.ondemandmonitoring.mission.dto.request.ExecutionPlanFinalizeRequest;
import com.ondemandmonitoring.mission.dto.response.ExecutionPlanResponse;
import com.ondemandmonitoring.mission.repository.*;
import com.ondemandmonitoring.mission.service.*;
import com.ondemandmonitoring.order.domain.Order;
import com.ondemandmonitoring.order.enums.*;
import com.ondemandmonitoring.order.repository.*;
import java.time.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
public class ExecutionPlanServiceImpl implements IExecutionPlanService {
    private final OrderRepository orders;
    private final MissionRepository missions;
    private final OrderChecklistItemRepository items;
    private final MissionChecklistAssignmentRepository assignments;
    private final IMissionChecklistMaterializationService materialization;
    private final IMissionChecklistScopeService scope;
    private final com.ondemandmonitoring.user.service.AuthenticatedUserResolver currentUser;
    private final ExecutionPlanCoverageService coverage;
    private final com.ondemandmonitoring.order.service.IOrderExecutionCompletionPolicy completion;
    private final com.ondemandmonitoring.finance.repository.InvoiceRepository invoices;
    private final com.ondemandmonitoring.finance.service.IMissionPaymentEligibilityService paymentEligibility;

    @Override
    @Transactional(readOnly = true)
    public ExecutionPlanResponse get(String orderId) {
        return response(orders.findById(orderId).orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND)), children(orderId));
    }

    @Override
    @Transactional
    public com.ondemandmonitoring.mission.dto.response.ExecutionPlanAmendResponse amend(String orderId,
            com.ondemandmonitoring.mission.dto.request.ExecutionPlanAmendRequest request) {
        if (request == null || request.expectedPlanRevision() == null || request.expectedPlanRevision() < 0)
            throw new ApiException(ErrorCode.INVALID_REQUEST);
        if (request.reason() == null || request.reason().isBlank())
            throw new ApiException(ErrorCode.EXECUTION_PLAN_AMENDMENT_REASON_REQUIRED);
        if (request.reason().length() > 1000) throw new ApiException(ErrorCode.INVALID_REQUEST);
        var order = orders.findByIdForUpdate(orderId).orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));
        if (order.getExecutionPlanRevision() != request.expectedPlanRevision() || order.getExecutionPlanRevision() == Long.MAX_VALUE)
            throw new ApiException(ErrorCode.EXECUTION_PLAN_REVISION_CONFLICT);
        if (order.getExecutionPlanStatus() != ExecutionPlanStatus.FINALIZED)
            throw new ApiException(ErrorCode.EXECUTION_PLAN_NOT_FINALIZED);
        if (order.getOrderStatus() != OrderStatus.APPROVED && order.getOrderStatus() != OrderStatus.IN_PROGRESS)
            throw new ApiException(ErrorCode.INVALID_REQUEST, "Only accepted, unfinished Orders may be amended");
        var locked = missions.findIdsByOrderIdOrderById(orderId).stream().sorted()
                .map(id -> missions.findByIdForUpdate(id).orElseThrow(() -> new ApiException(ErrorCode.MISSION_NOT_FOUND))).toList();
        var effective = locked.stream().filter(ExecutionPlanCoverageService::effective).toList();
        if (effective.isEmpty()) throw new ApiException(ErrorCode.CHECKLIST_EXECUTION_INTEGRITY_INVALID);
        for (var mission : effective) {
            if (mission.getCreationIdempotencyKey() == null || mission.getFinalizedPlanRevision() == null)
                throw new ApiException(ErrorCode.MULTI_MISSION_LEGACY_ORDER_NOT_MIGRATED);
            scope.requireMaterialized(mission);
        }
        order.setExecutionPlanStatus(ExecutionPlanStatus.DRAFT);
        order.setExecutionPlanRevision(order.getExecutionPlanRevision() + 1);
        order.setExecutionPlanAmendedAt(Instant.now()); order.setExecutionPlanAmendedBy(currentUser.getCurrentUserId());
        order.setExecutionPlanAmendmentReason(request.reason().strip());
        // Finalization audit and per-Mission acceptance revision are retained verbatim.
        orders.saveAndFlush(order);
        return new com.ondemandmonitoring.mission.dto.response.ExecutionPlanAmendResponse(orderId,
                order.getExecutionPlanStatus(), order.getExecutionPlanRevision(), order.getExecutionPlanAmendedAt(),
                order.getExecutionPlanAmendmentReason());
    }

    @Override
    @Transactional
    public ExecutionPlanResponse finalizePlan(String orderId, ExecutionPlanFinalizeRequest request) {
        if (request == null || request.expectedPlanRevision() == null || request.expectedPlanRevision() < 0
                || !Boolean.TRUE.equals(request.decompositionConfirmed())) throw new ApiException(ErrorCode.INVALID_REQUEST);
        var order = orders.findByIdForUpdate(orderId).orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));
        var invoice = invoices.findByOrderId(orderId)
                .orElseThrow(() -> new ApiException(ErrorCode.INVOICE_NOT_PAID));
        if (!paymentEligibility.isDepositSatisfied(invoice)) {
            throw new ApiException(ErrorCode.INVOICE_NOT_PAID,
                    "The required deposit must be paid before the execution plan can be finalized");
        }
        // Order serializes creation, assignment mutation and finalization; Mission locks protect runtime.
        var children = missions.findIdsByOrderIdOrderById(orderId).stream().sorted()
                .map(id -> missions.findByIdForUpdate(id).orElseThrow(() -> new ApiException(ErrorCode.MISSION_NOT_FOUND))).toList();
        var effective = children.stream().filter(ExecutionPlanCoverageService::effective).toList();
        long revision = order.getExecutionPlanRevision();
        if (order.getExecutionPlanStatus() == ExecutionPlanStatus.FINALIZED) {
            // Exact finalize retry may supply the consumed revision. No writes/materialization on replay.
            if (request.expectedPlanRevision() != revision && request.expectedPlanRevision() != revision - 1)
                throw new ApiException(ErrorCode.EXECUTION_PLAN_REVISION_CONFLICT);
            if (effective.isEmpty()) throw new ApiException(ErrorCode.CHECKLIST_EXECUTION_INTEGRITY_INVALID);
            effective.forEach(scope::requireMaterialized);
            var replay = response(order, children);
            if (!replay.coverage().coverageReady()) throw new ApiException(ErrorCode.CHECKLIST_COVERAGE_INCOMPLETE);
            return replay;
        }
        if (order.getExecutionPlanStatus() != ExecutionPlanStatus.DRAFT) throw new ApiException(ErrorCode.EXECUTION_PLAN_NOT_DRAFT);
        if (request.expectedPlanRevision() != revision || revision == Long.MAX_VALUE)
            throw new ApiException(ErrorCode.EXECUTION_PLAN_REVISION_CONFLICT);
        if ((order.getOrderStatus() != OrderStatus.APPROVED && order.getOrderStatus() != OrderStatus.IN_PROGRESS) || effective.isEmpty())
            throw new ApiException(ErrorCode.INVALID_REQUEST, "An approved Order and at least one effective planning Mission are required");
        effective.forEach(this::validateStructure);
        var plan = response(order, children);
        if (!plan.coverage().coverageReady()) throw new ApiException(ErrorCode.CHECKLIST_COVERAGE_INCOMPLETE);
        // All validation precedes materialization. Any failure still rolls back the entire transaction.
        var fresh = effective.stream().filter(m -> m.getFinalizedPlanRevision() == null).toList();
        fresh.forEach(materialization::materialize);
        long finalizedRevision = revision + 1;
        fresh.forEach(m -> m.setFinalizedPlanRevision(finalizedRevision));
        order.setExecutionPlanStatus(ExecutionPlanStatus.FINALIZED);
        order.setExecutionPlanRevision(finalizedRevision);
        order.setExecutionPlanFinalizedAt(Instant.now());
        order.setExecutionPlanFinalizedBy(currentUser.getCurrentUserId());
        missions.saveAllAndFlush(fresh);
        orders.saveAndFlush(order);
        effective.forEach(scope::requireMaterialized);
        // Reopening DRAFT may have deferred acceptance of already-complete unchanged packages.
        // All aggregate locks are already held; reuse the sole Phase 6A writer, never a late Order lock.
        completion.completeIfReady(new com.ondemandmonitoring.order.service.IOrderExecutionCompletionPolicy.LockedExecution(order, children));
        return response(order, children);
    }

    private void validateStructure(Mission mission) {
        if (mission.getCreationIdempotencyKey() == null) throw new ApiException(ErrorCode.MULTI_MISSION_LEGACY_ORDER_NOT_MIGRATED);
        if (mission.getReplacesMissionId() != null) {
            var original = missions.findById(mission.getReplacesMissionId()).orElseThrow(() -> new ApiException(ErrorCode.MISSION_NOT_FOUND));
            if (!mission.getOrder().getId().equals(original.getOrder().getId())) throw new ApiException(ErrorCode.MISSION_REPLACEMENT_CROSS_ORDER);
            if (original.getStatus() != com.ondemandmonitoring.mission.enums.MissionStatus.CANCELLED)
                throw new ApiException(ErrorCode.MISSION_REPLACEMENT_ORIGINAL_NOT_CANCELLED);
        }
        if (mission.getFinalizedPlanRevision() != null) {
            if (!scope.isMaterialized(mission)) throw new ApiException(ErrorCode.CHECKLIST_EXECUTION_INTEGRITY_INVALID);
            return; // Do not revalidate/change historical dates, states or materialize accepted packages.
        }
        if (mission.getStatus() != com.ondemandmonitoring.mission.enums.MissionStatus.RESOURCE_ASSIGNING
                || mission.getActualStartAt() != null || mission.getCompletedAt() != null)
            throw new ApiException(ErrorCode.EXECUTION_PLAN_NOT_DRAFT);
        var order = mission.getOrder();
        var start = mission.getScheduledStartAt(); var end = mission.getScheduledEndAt();
        var zone = ZoneId.of("Asia/Ho_Chi_Minh");
        if (mission.getScopeLabel() == null || mission.getScopeLabel().isBlank() || start == null || end == null
                || !start.isBefore(end) || order.getPreferredDateFrom() == null || order.getPreferredDateTo() == null
                || start.isBefore(order.getPreferredDateFrom().atStartOfDay(zone).toInstant())
                || end.isAfter(order.getPreferredDateTo().plusDays(1).atStartOfDay(zone).toInstant()))
            throw new ApiException(ErrorCode.INVALID_REQUEST, "Invalid Mission scope or schedule");
        var point = mission.getTargetPoint(); var area = mission.getTargetArea();
        if (point == null || point.getSRID() != 4326 || point.isEmpty() || !point.isValid()
                || !Double.isFinite(point.getX()) || !Double.isFinite(point.getY())
                || Math.abs(point.getX()) > 180 || Math.abs(point.getY()) > 90
                || (area != null && (area.getSRID() != 4326 || area.isEmpty() || !area.isValid() || !area.covers(point))))
            throw new ApiException(ErrorCode.INVALID_REQUEST, "Invalid WGS84 Mission geometry");
    }

    private List<Mission> children(String orderId) { return missions.findAllByOrderIdOrderByScheduledStartAtAscIdAsc(orderId); }

    private ExecutionPlanResponse response(Order order, List<Mission> children) {
        var active = assignments.findActiveByOrderId(order.getId());
        var calculated = coverage.evaluate(items.findAllByOrderIdInOrderByDisplayOrderAscIdAsc(List.of(order.getId())), children, active);
        var blockers = new ArrayList<String>();
        if (children.stream().noneMatch(ExecutionPlanCoverageService::effective)) blockers.add("NO_EFFECTIVE_MISSIONS");
        if (!calculated.coverageReady()) blockers.add("CHECKLIST_COVERAGE_INCOMPLETE");
        if (order.getOrderStatus() != OrderStatus.APPROVED && order.getOrderStatus() != OrderStatus.IN_PROGRESS) blockers.add("ORDER_NOT_APPROVED");
        if (order.getExecutionPlanStatus() == ExecutionPlanStatus.DRAFT) {
            for (var mission : children.stream().filter(ExecutionPlanCoverageService::effective).toList()) {
                try { validateStructure(mission); }
                catch (ApiException exception) { blockers.add(exception.getErrorCode().name() + ":" + mission.getId()); }
            }
        }
        return new ExecutionPlanResponse(order.getId(), order.getExecutionPlanStatus(), order.getExecutionPlanRevision(),
                children.stream().map(m -> new ExecutionPlanResponse.MissionScope(m.getId(), m.getScopeLabel(),
                        (int) active.stream().filter(a -> m.getId().equals(a.getMissionId())).count(), m.getFinalizedPlanRevision())).toList(),
                calculated, List.copyOf(blockers));
    }
}
