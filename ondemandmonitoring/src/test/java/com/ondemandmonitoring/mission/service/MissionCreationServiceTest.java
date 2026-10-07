package com.ondemandmonitoring.mission.service;

import com.ondemandmonitoring.common.exception.*;
import com.ondemandmonitoring.mission.domain.Mission;
import com.ondemandmonitoring.mission.dto.request.MissionCreateRequest;
import com.ondemandmonitoring.mission.service.impl.MissionCreationIntent;
import com.ondemandmonitoring.order.domain.Order;
import com.ondemandmonitoring.order.enums.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.locationtech.jts.geom.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MissionCreationServiceTest {
    MissionServiceTest fixture;
    Order order;
    static MissionCreateRequest request(String orderId, String key, long revision) {
        var r = new MissionCreateRequest(); r.setOrderId(orderId); r.setIdempotencyKey(key);
        r.setExpectedPlanRevision(revision); r.setScopeLabel("Whole Order scope");
        r.setScheduledStartAt(Instant.parse("2026-10-08T16:30:00Z"));
        r.setScheduledEndAt(Instant.parse("2026-10-09T01:30:00Z")); return r;
    }
    @BeforeEach void setup() {
        fixture = new MissionServiceTest(); fixture.setUp();
        order = Order.builder().orderStatus(OrderStatus.APPROVED).preferredDateFrom(LocalDate.of(2026,10,8))
                .preferredDateTo(LocalDate.of(2026,10,10)).build(); order.setId("o");
        var geometry = new GeometryFactory(new PrecisionModel(),4326);
        order.setPoint(geometry.createPoint(new Coordinate(106,10)));
        when(fixture.orderRepository.findByIdForUpdate("o")).thenReturn(Optional.of(order));
        when(fixture.missionRepository.saveAndFlush(any())).thenAnswer(inv -> {
            Mission m = inv.getArgument(0); m.setId(UUID.randomUUID().toString()); return m;
        });
    }
    MissionCreateRequest request() { return request("o",UUID.randomUUID().toString(),0); }
    void error(ErrorCode code, Runnable action) { assertEquals(code,assertThrows(ApiException.class,action::run).getErrorCode()); }
    @Test void newCreationCopiesScopeAndIncrementsOnlyPublicPlanRevision() {
        var r=request(); order.setVersion(7L);
        var created=fixture.missionService.createMissionWithOutcome(r);
        assertTrue(created.created()); assertEquals(1,order.getExecutionPlanRevision()); assertEquals(7L,order.getVersion());
        var captor=org.mockito.ArgumentCaptor.forClass(Mission.class);
        verify(fixture.missionRepository).saveAndFlush(captor.capture());
        var m=captor.getValue(); assertNotSame(order.getPoint(),m.getTargetPoint());
        assertEquals(order.getPoint(),m.getTargetPoint()); assertEquals(64,m.getCreationRequestHash().length());
        assertNull(m.getFinalizedPlanRevision());
    }
    @Test void replayPrecedesStateAndRevisionGuardsAndUsesOriginalCopiedScope() {
        var r=request(); var first=fixture.missionService.createMissionWithOutcome(r);
        var captor=org.mockito.ArgumentCaptor.forClass(Mission.class); verify(fixture.missionRepository).saveAndFlush(captor.capture());
        var original=captor.getValue();
        when(fixture.missionRepository.findByOrderIdAndCreationIdempotencyKey("o",r.getIdempotencyKey())).thenReturn(Optional.of(original));
        order.setExecutionPlanStatus(ExecutionPlanStatus.FINALIZED); order.setOrderStatus(OrderStatus.COMPLETED);
        order.setExecutionPlanRevision(42); order.setPoint(null); original.setStatus(com.ondemandmonitoring.mission.enums.MissionStatus.COMPLETED);
        r.setExpectedPlanRevision(999L); r.setScopeLabel("  Whole   Order scope  ");
        var replay=fixture.missionService.createMissionWithOutcome(r);
        assertFalse(replay.created()); assertEquals(first.mission().getId(),replay.mission().getId());
        assertEquals(42,order.getExecutionPlanRevision()); verify(fixture.missionRepository,times(1)).saveAndFlush(any());
        var sequence=inOrder(fixture.orderRepository,fixture.missionRepository);
        sequence.verify(fixture.orderRepository).findByIdForUpdate("o");
        sequence.verify(fixture.missionRepository).findByOrderIdAndCreationIdempotencyKey("o",r.getIdempotencyKey());
        r.setScheduledEndAt(r.getScheduledEndAt().plusSeconds(1));
        error(ErrorCode.IDEMPOTENCY_KEY_REUSED,()->fixture.missionService.createMission(r));
    }
    @Test void staleRevisionDoesNotCreate() { var r=request(); r.setExpectedPlanRevision(2L); error(ErrorCode.EXECUTION_PLAN_REVISION_CONFLICT,()->fixture.missionService.createMission(r)); verify(fixture.missionRepository,never()).saveAndFlush(any()); }
    @Test void finalizedPlanRejectsNewKey() {order.setExecutionPlanStatus(ExecutionPlanStatus.FINALIZED); error(ErrorCode.EXECUTION_PLAN_NOT_DRAFT,()->fixture.missionService.createMission(request()));}
    @ParameterizedTest @EnumSource(value=OrderStatus.class,names={"COMPLETED","CANCELLED","REJECTED","IN_PROGRESS","PENDING"})
    void nonApprovedOrderRejectsNewKey(OrderStatus state) {order.setOrderStatus(state); error(ErrorCode.INVALID_REQUEST,()->fixture.missionService.createMission(request()));}
    @Test void legacyOrderCannotMixNewPlanningSibling() {when(fixture.missionRepository.findAllByOrderIdOrderByScheduledStartAtAscIdAsc("o")).thenReturn(List.of(new Mission())); error(ErrorCode.MULTI_MISSION_LEGACY_ORDER_NOT_MIGRATED,()->fixture.missionService.createMission(request()));}
    @Test void validatesBothBusinessDateBoundsAndPositiveDuration() {
        var r=request(); r.setScheduledStartAt(Instant.parse("2026-10-07T16:59:00Z")); var early=r; error(ErrorCode.INVALID_REQUEST,()->fixture.missionService.createMission(early));
        r=request(); r.setScheduledEndAt(Instant.parse("2026-10-10T17:00:01Z")); var late=r; error(ErrorCode.INVALID_REQUEST,()->fixture.missionService.createMission(late));
        r=request(); r.setScheduledEndAt(r.getScheduledStartAt()); var empty=r; error(ErrorCode.INVALID_REQUEST,()->fixture.missionService.createMission(empty));
    }
    @Test void malformedAndDeferredPayloadsAreNotSilentlyIgnored() {
        var r=request(); r.setIdempotencyKey("bad"); error(ErrorCode.INVALID_REQUEST,()->fixture.missionService.createMission(r));
        r.setIdempotencyKey(UUID.randomUUID().toString()); r.setChecklistItemIds(List.of("other-order-item")); error(ErrorCode.INVALID_REQUEST,()->fixture.missionService.createMission(r));
        r.setChecklistItemIds(null); r.setTargetPoint(com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode()); error(ErrorCode.INVALID_REQUEST,()->fixture.missionService.createMission(r));
    }
    @Test void deterministicHashExcludesKeyRevisionAndCanonicalizesGeometryAndText() {
        var a=request(); var b=request(); b.setExpectedPlanRevision(100L); b.setScopeLabel(" Whole  Order scope ");
        assertEquals(MissionCreationIntent.from(a).hash(order.getPoint(),null),MissionCreationIntent.from(b).hash(order.getPoint(),null));
        var geometry=new GeometryFactory(new PrecisionModel(),4326);
        var p=geometry.createPolygon(new Coordinate[]{new Coordinate(0,0),new Coordinate(1,0),new Coordinate(1,1),new Coordinate(0,0)});
        assertEquals(MissionCreationIntent.from(a).hash(order.getPoint(),p),MissionCreationIntent.from(b).hash(order.getPoint(),p.reverse()));
    }
    @Test void planningMissionCannotEnterLifecycleOrTelemetryEvenWithForcedRuntimeState() {
        var mission=new Mission(); mission.setId("m"); mission.setCreationIdempotencyKey(UUID.randomUUID().toString());
        mission.setStatus(com.ondemandmonitoring.mission.enums.MissionStatus.IN_FLIGHT);
        when(fixture.missionRepository.findById("m")).thenReturn(Optional.of(mission));
        error(ErrorCode.CHECKLIST_ASSIGNMENT_NOT_FINALIZED,()->fixture.deviceConnectionService.connectGcs("m"));
        error(ErrorCode.CHECKLIST_ASSIGNMENT_NOT_FINALIZED,()->fixture.missionService.completeMission("m"));
        verifyNoInteractions(fixture.missionDeviceAssignmentRepository,fixture.missionStaffAssignmentRepository);
    }
    @Test void crewMayReadTheirPlanningMissionButCannotGainOperationalPermissions() {
        var mission=new Mission();mission.setId("m");mission.setCreationIdempotencyKey(UUID.randomUUID().toString());
        mission.setStatus(com.ondemandmonitoring.mission.enums.MissionStatus.IN_FLIGHT);
        var caller=com.ondemandmonitoring.user.domain.User.builder().isActive(true).role(com.ondemandmonitoring.role.domain.Role.builder().code(com.ondemandmonitoring.role.domain.RoleCode.STAFF).active(true).build()).build();caller.setId("crew");
        when(fixture.authenticatedUserResolver.getCurrentUser()).thenReturn(caller);
        when(fixture.missionRepository.findById("m")).thenReturn(Optional.of(mission));
        when(fixture.missionStaffAssignmentRepository.existsByMissionIdAndStaffId("m","crew")).thenReturn(true);
        var auth=new com.ondemandmonitoring.mission.service.impl.MissionAuthorizationServiceImpl(fixture.missionStaffAssignmentRepository,fixture.missionRepository,fixture.authenticatedUserResolver,mock(com.ondemandmonitoring.mission.repository.MissionResultRepository.class), ScopeTestFixture.validScope());
        assertTrue(auth.canViewMission("m"));assertFalse(auth.canControlFlight("m"));assertFalse(auth.canOperatePayload("m"));
        assertFalse(auth.canExecuteMonitoringChecklist("m"));assertFalse(auth.canUploadMissionMedia("m"));
        assertFalse(auth.canInspectDevice("m"));assertFalse(auth.canMaintainDevice("m"));assertFalse(auth.canAttachChecklistEvidence("m"));
    }
    @Test void forgedPlanningResultCannotApproveOrCompleteOrder() {
        var mission=new Mission();mission.setId("m");mission.setOrder(order);mission.setCreationIdempotencyKey(UUID.randomUUID().toString());
        mission.setStatus(com.ondemandmonitoring.mission.enums.MissionStatus.COMPLETED);
        var result=new com.ondemandmonitoring.mission.domain.MissionResult();result.setMission(mission);result.setApprovalStatus(com.ondemandmonitoring.mission.enums.MissionResultApprovalStatus.PENDING_MANAGER_APPROVAL);
        var results=mock(com.ondemandmonitoring.mission.repository.MissionResultRepository.class);
        when(results.findOrderIdByResultId("r")).thenReturn(Optional.of(order.getId()));when(results.findById("r")).thenReturn(Optional.of(result));
        when(fixture.orderRepository.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));
        when(fixture.missionRepository.findIdsByOrderIdOrderById(order.getId())).thenReturn(List.of("m"));
        when(fixture.missionRepository.findByIdForUpdate("m")).thenReturn(Optional.of(mission));
        var checks=mock(IMissionChecklistExecutionService.class);
        var service=new com.ondemandmonitoring.mission.service.impl.MissionResultService(results,fixture.missionRepository,null,null,null,
                new com.ondemandmonitoring.order.service.impl.OrderExecutionCompletionPolicy(fixture.orderRepository,fixture.missionRepository,results,checks),
                checks,fixture.authenticatedUserResolver,null);
        error(ErrorCode.CHECKLIST_ASSIGNMENT_NOT_FINALIZED,()->service.approve("r",null));
        assertEquals(OrderStatus.APPROVED,order.getOrderStatus());verifyNoInteractions(checks);verify(fixture.orderRepository,never()).save(any());
    }
}
