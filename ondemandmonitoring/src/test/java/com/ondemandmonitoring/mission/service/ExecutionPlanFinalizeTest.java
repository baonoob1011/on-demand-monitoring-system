package com.ondemandmonitoring.mission.service;

import com.ondemandmonitoring.common.exception.*;
import com.ondemandmonitoring.mission.domain.*;
import com.ondemandmonitoring.mission.dto.request.ExecutionPlanFinalizeRequest;
import com.ondemandmonitoring.mission.repository.*;
import com.ondemandmonitoring.mission.service.impl.*;
import com.ondemandmonitoring.order.domain.Order;
import com.ondemandmonitoring.order.domain.OrderChecklistItem;
import com.ondemandmonitoring.order.enums.*;
import com.ondemandmonitoring.order.repository.*;
import com.ondemandmonitoring.user.service.AuthenticatedUserResolver;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.locationtech.jts.geom.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ExecutionPlanFinalizeTest {
    OrderRepository orders=mock(OrderRepository.class);
    MissionRepository missions=mock(MissionRepository.class);
    OrderChecklistItemRepository items=mock(OrderChecklistItemRepository.class);
    MissionChecklistAssignmentRepository assignments=mock(MissionChecklistAssignmentRepository.class);
    IMissionChecklistMaterializationService materializer=mock(IMissionChecklistMaterializationService.class);
    IMissionChecklistScopeService scope=mock(IMissionChecklistScopeService.class);
    AuthenticatedUserResolver actor=mock(AuthenticatedUserResolver.class);
    com.ondemandmonitoring.finance.repository.InvoiceRepository invoices=mock(com.ondemandmonitoring.finance.repository.InvoiceRepository.class);
    com.ondemandmonitoring.finance.service.IMissionPaymentEligibilityService paymentEligibility=mock(com.ondemandmonitoring.finance.service.IMissionPaymentEligibilityService.class);
    ExecutionPlanServiceImpl service=new ExecutionPlanServiceImpl(orders,missions,items,assignments,materializer,scope,actor,new ExecutionPlanCoverageService(),mock(com.ondemandmonitoring.order.service.IOrderExecutionCompletionPolicy.class),invoices,paymentEligibility);
    Order order; Mission first,second; OrderChecklistItem item;
    @BeforeEach void setup() {
        order=new Order(); order.setId("o"); order.setExecutionPlanRevision(3); order.setOrderStatus(OrderStatus.APPROVED);
        order.setPreferredDateFrom(LocalDate.of(2026,10,1)); order.setPreferredDateTo(LocalDate.of(2026,10,7));
        first=mission("a"); second=mission("b"); item=new OrderChecklistItem(); item.setId("i"); item.setOrder(order);
        when(orders.findByIdForUpdate("o")).thenReturn(Optional.of(order));
        var invoice=mock(com.ondemandmonitoring.finance.domain.Invoice.class);
        when(invoices.findByOrderId("o")).thenReturn(Optional.of(invoice));
        when(paymentEligibility.isDepositSatisfied(invoice)).thenReturn(true);
        when(missions.findIdsByOrderIdOrderById("o")).thenReturn(List.of("b","a"));
        when(missions.findByIdForUpdate("a")).thenReturn(Optional.of(first)); when(missions.findByIdForUpdate("b")).thenReturn(Optional.of(second));
        when(items.findAllByOrderIdInOrderByDisplayOrderAscIdAsc(List.of("o"))).thenReturn(List.of(item));
        var assignment=new MissionChecklistAssignment(); assignment.setId("assignment"); assignment.setMissionId("a");
        assignment.setOrderId("o"); assignment.setOrderChecklistItemId("i"); assignment.setOrderChecklistItem(item);
        when(assignments.findActiveByOrderId("o")).thenReturn(List.of(assignment)); when(actor.getCurrentUserId()).thenReturn("manager");
    }
    Mission mission(String id) {
        var m=new Mission(); m.setId(id); m.setOrder(order); m.setStatus(com.ondemandmonitoring.mission.enums.MissionStatus.PLANNING);
        m.setScopeLabel("Scope"); m.setScheduledStartAt(Instant.parse("2026-10-02T00:00:00Z"));
        m.setScheduledEndAt(m.getScheduledStartAt().plusSeconds(3600)); m.setTargetPoint(new GeometryFactory(new PrecisionModel(),4326).createPoint(new Coordinate(106,10))); return m;
    }
    void finalizePlan() { service.finalizePlan("o",new ExecutionPlanFinalizeRequest(3L,true)); }
    void error(ErrorCode code,Runnable action) { assertEquals(code,assertThrows(ApiException.class,action::run).getErrorCode()); }
    @Test void finalizationLocksInOrderThenSetsOneSharedRevisionAndAudit() {
        finalizePlan(); var sequence=inOrder(orders,missions,materializer);
        sequence.verify(orders).findByIdForUpdate("o"); sequence.verify(missions).findIdsByOrderIdOrderById("o");
        sequence.verify(missions).findByIdForUpdate("a"); sequence.verify(missions).findByIdForUpdate("b");
        sequence.verify(materializer).materialize(first); sequence.verify(materializer).materialize(second);
        assertEquals(4,order.getExecutionPlanRevision()); assertEquals(4L,first.getFinalizedPlanRevision()); assertEquals(4L,second.getFinalizedPlanRevision());
        assertEquals("manager",order.getExecutionPlanFinalizedBy()); assertNotNull(order.getExecutionPlanFinalizedAt());
        assertEquals(com.ondemandmonitoring.mission.enums.MissionStatus.READY,first.getStatus());
    }
    @Test void invalidCrossOrderAssignmentOnSecondMissionPreventsEveryMaterialization() {
        var foreign=new MissionChecklistAssignment(); foreign.setId("bad"); foreign.setMissionId("b"); foreign.setOrderId("foreign");
        foreign.setOrderChecklistItemId(item.getId()); foreign.setOrderChecklistItem(item);
        when(assignments.findActiveByOrderId("o")).thenReturn(List.of(foreign));
        error(ErrorCode.CHECKLIST_ASSIGNMENT_CROSS_ORDER,this::finalizePlan);
        verifyNoInteractions(materializer); assertEquals(ExecutionPlanStatus.DRAFT,order.getExecutionPlanStatus());
        assertNull(first.getFinalizedPlanRevision()); assertNull(second.getFinalizedPlanRevision()); assertNull(order.getExecutionPlanFinalizedAt());
    }
    @Test void noConfirmationOrStaleRevisionNeverMaterializes() {
        error(ErrorCode.INVALID_REQUEST,()->service.finalizePlan("o",new ExecutionPlanFinalizeRequest(3L,false)));
        error(ErrorCode.EXECUTION_PLAN_REVISION_CONFLICT,()->service.finalizePlan("o",new ExecutionPlanFinalizeRequest(2L,true)));
        verifyNoInteractions(materializer);
    }
    @Test void missingCoverageCannotFinalize() {
        when(assignments.findActiveByOrderId("o")).thenReturn(List.of());
        error(ErrorCode.CHECKLIST_COVERAGE_INCOMPLETE,this::finalizePlan); verifyNoInteractions(materializer);
    }
    @Test void unpaidDepositCannotFinalize() {
        when(paymentEligibility.isDepositSatisfied(any())).thenReturn(false);
        error(ErrorCode.INVOICE_NOT_PAID,this::finalizePlan);
        verifyNoInteractions(materializer);
    }
    @Test void alreadyFinalizedReplayDoesNotRematerialize() {
        finalizePlan(); clearInvocations(materializer); var result=service.finalizePlan("o",new ExecutionPlanFinalizeRequest(3L,true));
        assertEquals(4,result.revision()); verifyNoInteractions(materializer);
        error(ErrorCode.EXECUTION_PLAN_REVISION_CONFLICT,()->service.finalizePlan("o",new ExecutionPlanFinalizeRequest(1L,true)));
    }
}
