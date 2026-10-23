package com.ondemandmonitoring.checklist;

import com.ondemandmonitoring.common.exception.*;
import com.ondemandmonitoring.mission.domain.Mission;
import com.ondemandmonitoring.mission.dto.request.*;
import com.ondemandmonitoring.mission.enums.*;
import com.ondemandmonitoring.mission.repository.*;
import com.ondemandmonitoring.mission.service.*;
import com.ondemandmonitoring.mission.service.impl.*;
import com.ondemandmonitoring.order.enums.*;
import com.ondemandmonitoring.order.repository.OrderRepository;
import com.ondemandmonitoring.user.service.AuthenticatedUserResolver;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import static org.junit.jupiter.api.Assertions.*;

/** Opt-in local PostgreSQL: amendment, cancellation, replacement and unchanged historical packages. */
@SpringJUnitConfig(ExecutionPlanAmendmentPostgresTest.Beans.class)
class ExecutionPlanAmendmentPostgresTest extends OrderAggregateCompletionPostgresTest {
    @Autowired IMissionCancellationService cancellation;

    void amend() { plan.amend(orderId, new ExecutionPlanAmendRequest(revision(), "  Weather requires a new flight  ")); }
    void cancel(String mission) { cancellation.cancel(mission, new MissionCancelRequest(revision(), "  Unsafe original package  ")); }
    MissionCreateRequest replacementRequest(String original) {
        var r = new MissionCreateRequest();
        r.setScopeLabel("Replacement scope");
        var start = java.time.LocalDate.now().plusDays(7).atStartOfDay(java.time.ZoneOffset.UTC).toInstant();
        r.setScheduledStartAt(start); r.setScheduledEndAt(start.plusSeconds(3600)); return r;
    }
    String replacement(String original) { return missionService.replaceMission(original, replacementRequest(original)).getId(); }
    long finalizedRevision(String mission) { return jdbc.queryForObject("select finalized_plan_revision from missions where id=?", Long.class, mission); }
    String foreignOrder() {
        String id = UUID.randomUUID().toString();
        jdbc.update("insert into orders select (jsonb_populate_record(null::orders,to_jsonb(o)||jsonb_build_object('id',?,'order_code',?,'execution_plan_status','DRAFT','execution_plan_revision',0,'execution_plan_finalized_at',null,'execution_plan_finalized_by',null,'execution_plan_amended_at',null,'execution_plan_amended_by',null,'execution_plan_amendment_reason',null))).* from orders o where id=?", id,id,orderId);
        return id;
    }
    Map<String,Object> history(String mission) {
        var data = new LinkedHashMap<String,Object>();
        for (String table : List.of("mission_checklist_assignments", "mission_checklist_executions", "mission_checklist_evidence", "drone_media", "mission_results")) {
            data.put(table, jdbc.queryForObject("select coalesce(jsonb_agg(to_jsonb(t) order by id),'[]'::jsonb)::text from " + table + " t where mission_id=?", String.class, mission));
        }
        return data;
    }
    void failFirst() { jdbc.update("update missions set status='FAILED' where id=?", first); }

    @Test void amendRetainsFinalizationAuditAndMissionHistoryAndOnlyAdvancesPlanRevision() {
        ready(1); long accepted = finalizedRevision(first), revision = revision();
        var audit = jdbc.queryForMap("select execution_plan_finalized_at,execution_plan_finalized_by from orders where id=?", orderId);
        var before = history(first); amend();
        assertEquals(revision + 1, revision()); assertEquals(accepted, finalizedRevision(first)); assertEquals(before, history(first));
        assertEquals(audit, jdbc.queryForMap("select execution_plan_finalized_at,execution_plan_finalized_by from orders where id=?", orderId));
        assertEquals("DRAFT", jdbc.queryForObject("select execution_plan_status from orders where id=?", String.class, orderId));
        assertEquals("Weather requires a new flight", jdbc.queryForObject("select execution_plan_amendment_reason from orders where id=?", String.class, orderId));
        assertNotNull(jdbc.queryForObject("select execution_plan_amended_at from orders where id=?", Object.class, orderId));
    }
    @Test void amendRejectsStaleBlankAndTerminalOrdersWithoutRevisionChanges() {
        ready(0); long before = revision();
        error(ErrorCode.EXECUTION_PLAN_AMENDMENT_REASON_REQUIRED, () -> plan.amend(orderId, new ExecutionPlanAmendRequest(before, " ")));
        error(ErrorCode.EXECUTION_PLAN_REVISION_CONFLICT, () -> plan.amend(orderId, new ExecutionPlanAmendRequest(before - 1, "Reason")));
        for (String status : List.of("COMPLETED", "CANCELLED", "REJECTED")) {
            jdbc.update("update orders set order_status=? where id=?", status, orderId);
            error(ErrorCode.INVALID_REQUEST, this::amend);
        }
        assertEquals(before, revision());
    }
    @Test void legacyAmendmentFailsClosedWithoutFabricatingMetadata() {
        ready(0); jdbc.update("update missions set finalized_plan_revision=null where order_id=?", orderId);
        long before = revision(); error(ErrorCode.MULTI_MISSION_LEGACY_ORDER_NOT_MIGRATED, this::amend);
        assertEquals(before, revision()); assertNull(jdbc.queryForObject("select execution_plan_amended_at from orders where id=?", Object.class, orderId));
    }
    @Test void failedReplacementRestoresCoverageWithoutCopyingHistoryAndRetainsOldAcceptanceRevision() {
        fixture(3); choose(first,0,1); choose(second,2); finalizePlan();
        String media = asset(first); var row = runtime.getByMissionId(first).executions().getFirst();
        evidence.attach(first, new BatchChecklistEvidenceRequest(media, List.of(new BatchChecklistEvidenceRequest.Target(row.getId(), row.getVersion())), null));
        result(first, MissionResultApprovalStatus.DRAFT);
        // Existing operational lifecycle releases hardware before planning cancellation.
        jdbc.update("update mission_device_assignments set released_at=now(),is_current=false where mission_id=?", first);
        failFirst(); long oldRevision = finalizedRevision(second); var before = history(first);
        var siblingExecutionIds = runtime.getByMissionId(second).executions().stream().map(rowEntry -> rowEntry.getId()).toList();
        amend(); cancel(first); assertEquals(before, history(first));
        assertEquals(2, plan.get(orderId).coverage().uncovered());
        error(ErrorCode.CHECKLIST_COVERAGE_INCOMPLETE, this::finalizePlan);
        String third = replacement(first); choose(third,0,1);
        assertEquals(0, jdbc.queryForObject("select count(*) from mission_checklist_executions where mission_id=?", Integer.class, third));
        transaction.executeWithoutResult(tx -> assertFalse(scope.isMaterialized(missions.findById(third).orElseThrow())));
        finalizePlan(); assertEquals(before, history(first)); assertEquals(oldRevision, finalizedRevision(second));
        assertEquals(siblingExecutionIds, runtime.getByMissionId(second).executions().stream().map(rowEntry -> rowEntry.getId()).toList());
        assertEquals(revision(), finalizedRevision(third)); assertEquals(2, jdbc.queryForObject("select count(*) from mission_checklist_executions where mission_id=?", Integer.class, third));
        assertEquals(0, jdbc.queryForObject("select count(*) from mission_checklist_evidence where mission_id=?", Integer.class, third));
        assertFalse(resultRepository.existsByMissionId(third));
        long count = rows("mission_checklist_executions"); finalizePlan(); assertEquals(count, rows("mission_checklist_executions"));
    }
    @Test void failedReplacementInsertDoesNotPoisonKeyOrMutateHistory() {
        ready(0); amend(); cancel(first);
        var request = replacementRequest(first);
        long beforeRevision = revision();
        var originalHistory = history(first);
        var siblingHistory = history(second);
        jdbc.execute("alter table missions add constraint fixture_reject_replacement check (replaces_mission_id is null)");
        try {
            assertThrows(jakarta.persistence.PersistenceException.class, () -> missionService.replaceMission(first, request));
            assertEquals(beforeRevision, revision());
            assertEquals(originalHistory, history(first));
            assertEquals(siblingHistory, history(second));
        } finally {
            jdbc.execute("alter table missions drop constraint fixture_reject_replacement");
        }
        var retry = missionService.replaceMission(first, request);
        assertNotNull(retry.getId());
        assertEquals(originalHistory, history(first)); assertEquals(siblingHistory, history(second));
    }

    @Test void existingFinalizedRuntimeContinuesDuringDraftWhileNewMissionRemainsBlocked() {
        ready(0); amend(); String third = create();
        transaction.executeWithoutResult(tx -> {
            assertTrue(scope.isMaterialized(missions.findById(first).orElseThrow()));
            assertFalse(scope.isMaterialized(missions.findById(third).orElseThrow()));
        });
        resultService.approve(result(first, MissionResultApprovalStatus.PENDING_MANAGER_APPROVAL), null);
        assertEquals(OrderStatus.APPROVED, status());
        error(ErrorCode.EXECUTION_PLAN_NOT_DRAFT, () -> choose(first));
        finalizePlan(); transaction.executeWithoutResult(tx -> assertTrue(scope.isMaterialized(missions.findById(third).orElseThrow())));
    }
    @Test void approvedOriginalCannotCancelOrReplaceAndKeepsHistory() {
        ready(1); result(first, MissionResultApprovalStatus.APPROVED); var before = history(first); amend();
        error(ErrorCode.MISSION_RESULT_ALREADY_APPROVED, () -> cancel(first));
        error(ErrorCode.MISSION_RESULT_ALREADY_APPROVED, () -> replacement(first)); assertEquals(before, history(first));
    }
    @Test void activeFlightAndUnreleasedResourcesRequireRealSafeStop() {
        ready(0); amend();
        for (String state : List.of("IN_FLIGHT","IN_PROGRESS","RETURNING","CONNECTED","PREFLIGHT_CHECKING","READY_TO_FLY","POSTFLIGHT_CHECKING","PENDING_REVIEW")) {
            jdbc.update("update missions set status=? where id=?", state, first);
            error(ErrorCode.MISSION_CANCELLATION_REQUIRES_SAFE_STOP, () -> cancel(first));
        }
        failFirst(); asset(first);
        error(ErrorCode.MISSION_CANCELLATION_REQUIRES_SAFE_STOP, () -> cancel(first));
    }
    @Test void completedUnacceptedResultsCanCancelWithoutDeletingHistory() {
        for (var state : List.of(MissionResultApprovalStatus.DRAFT, MissionResultApprovalStatus.PENDING_MANAGER_APPROVAL, MissionResultApprovalStatus.REJECTED)) {
            prepare(); ready(1); result(first, state); var before = history(first); amend(); cancel(first);
            assertEquals(before, history(first));
        }
    }
    @Test void cancellationRequiresFinalizedAndRevisionAndRejectsReplayWithoutNewAudit() {
        ready(0); long before = revision();
        error(ErrorCode.EXECUTION_PLAN_REVISION_CONFLICT, () -> cancellation.cancel(first, new MissionCancelRequest(before - 1, "Reason")));
        cancel(first); var audit = jdbc.queryForMap("select cancellation_at,cancelled_by,cancellation_reason,version from missions where id=?", first);
        error(ErrorCode.MISSION_ALREADY_CANCELLED, () -> cancel(first));
        amend();
        error(ErrorCode.EXECUTION_PLAN_NOT_FINALIZED, () -> cancel(second));
        assertEquals(before + 1, revision()); assertEquals(audit, jdbc.queryForMap("select cancellation_at,cancelled_by,cancellation_reason,version from missions where id=?", first));
    }
    @Test void replacementMustBeSameOrderCancelledAndUniqueAndHashBindsOriginal() {
        ready(0); amend(); error(ErrorCode.MISSION_REPLACEMENT_ORIGINAL_NOT_CANCELLED, () -> replacement(first)); cancel(first);
        var request = replacementRequest(first); String replacement = missionService.replaceMission(first, request).getId();
        assertNotNull(replacement);
        error(ErrorCode.MISSION_REPLACEMENT_ALREADY_EXISTS, () -> missionService.replaceMission(first, request));
        error(ErrorCode.MISSION_REPLACEMENT_ALREADY_EXISTS, () -> replacement(first));
    }
    @Test void cancelledReplacementCanBeReplacedInAChainOrAllowAnotherActiveDirectBranch() {
        ready(0); amend(); cancel(first); String r2 = replacement(first); cancel(r2);
        String r3 = replacement(r2); assertNotEquals(r2,r3);
        String direct = replacement(first); assertNotEquals(r3,direct);
        error(ErrorCode.MISSION_REPLACEMENT_ALREADY_EXISTS, () -> replacement(first));
    }
    @Test void finalizedStructureCannotBeEditedButNewPlanningSelectionCan() {
        ready(1); amend(); error(ErrorCode.EXECUTION_PLAN_NOT_DRAFT, () -> choose(first,0));
        var update = new MissionUpdateRequest();
        error(ErrorCode.EXECUTION_PLAN_NOT_DRAFT, () -> missionService.updateMission(first, update));
        String third = create(); choose(third,0); assertEquals(1, selection.get(third).assignments().size());
        var previous = transaction.execute(tx -> missions.findById(third).orElseThrow().getScheduledEndAt());
        update.setScheduledEndAt(previous.plusSeconds(60)); missionService.updateMission(third,update);
        assertEquals(previous.plusSeconds(60),transaction.execute(tx -> missions.findById(third).orElseThrow().getScheduledEndAt()));
        missionService.updateMission(third,update);
    }
    @Test void aggregateCompletesOnlyAfterRefinalizationAndAllEffectiveResultsApproved() {
        ready(0); failFirst(); amend(); cancel(first); String third = replacement(first); finalizePlan();
        resultService.approve(result(second, MissionResultApprovalStatus.PENDING_MANAGER_APPROVAL), null);
        assertEquals(OrderStatus.APPROVED, status());
        jdbc.update("update missions set status='COMPLETED',completed_at=now() where id=?", third);
        resultService.approve(result(third, MissionResultApprovalStatus.PENDING_MANAGER_APPROVAL), null);
        assertEquals(OrderStatus.COMPLETED, status());
    }
    @Test void refinalizeRetriggersCanonicalCompletionForResultsAcceptedWhileDraft() {
        ready(0); amend();
        resultService.approve(result(first,MissionResultApprovalStatus.PENDING_MANAGER_APPROVAL),null);
        resultService.approve(result(second,MissionResultApprovalStatus.PENDING_MANAGER_APPROVAL),null);
        assertEquals(OrderStatus.APPROVED,status()); finalizePlan(); assertEquals(OrderStatus.COMPLETED,status());
    }
    List<Object> race(Callable<?> one, Callable<?> two) throws Exception {
        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var a = pool.submit(() -> { start.await(); try { return one.call(); } catch (ApiException e) { return e.getErrorCode(); } });
            var b = pool.submit(() -> { start.await(); try { return two.call(); } catch (ApiException e) { return e.getErrorCode(); } });
            start.countDown(); return List.of(a.get(20,TimeUnit.SECONDS),b.get(20,TimeUnit.SECONDS));
        }
    }
    @Test void concurrentAmendmentsConsumeExactlyOneRevision() throws Exception {
        ready(0); long revision = revision(); var command = new ExecutionPlanAmendRequest(revision,"Reason");
        var outcomes = race(() -> plan.amend(orderId, command), () -> plan.amend(orderId, command));
        assertEquals(1, outcomes.stream().filter(x -> x == ErrorCode.EXECUTION_PLAN_REVISION_CONFLICT).count()); assertEquals(revision+1, revision());
    }
    @Test void concurrentCancellationsPreserveOneAuditAndOneRevision() throws Exception {
        ready(0); amend(); long revision = revision(); var command = new MissionCancelRequest(revision,"Reason");
        var outcomes = race(() -> cancellation.cancel(first,command), () -> cancellation.cancel(first,command));
        assertEquals(1,outcomes.stream().filter(x -> x == ErrorCode.MISSION_ALREADY_CANCELLED).count()); assertEquals(revision+1,revision());
    }
    @Test void concurrentReplacementsHaveOneActiveWinner() throws Exception {
        ready(0); amend(); cancel(first); var a = replacementRequest(first); var b = replacementRequest(first); long before = revision();
        var outcomes = race(() -> missionService.replaceMission(first, a), () -> missionService.replaceMission(first, b));
        assertEquals(1,outcomes.stream().filter(x -> x == ErrorCode.EXECUTION_PLAN_REVISION_CONFLICT || x == ErrorCode.MISSION_REPLACEMENT_ALREADY_EXISTS).count());
        assertEquals(before+1,revision()); assertEquals(1,jdbc.queryForObject("select count(*) from missions where replaces_mission_id=? and status<>'CANCELLED'",Integer.class,first));
    }
    @Test void partialUniqueAndCompositeFkProtectDirectDatabaseWrites() {
        ready(0); amend(); cancel(first); String third = replacement(first); String fourth = create();
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class, () -> jdbc.update("update missions set replaces_mission_id=? where id=?", first,fourth));
        String foreign = foreignOrder();
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class, () -> jdbc.update("update missions set order_id=? where id=?", foreign,third));
        assertNull(jdbc.queryForObject("select replaces_mission_id from missions where id=?",String.class,fourth));
    }

    @Configuration @Import(OrderAggregateCompletionPostgresTest.Beans.class)
    static class Beans {
        @Bean @Primary IMissionService amendmentMissionService(MissionRepository missions,OrderRepository orders,
                IMissionChecklistExecutionService executions,AuthenticatedUserResolver actor) {
            var mapper = org.mockito.Mockito.mock(com.ondemandmonitoring.mission.mapper.MissionMapper.class);
            org.mockito.Mockito.when(mapper.toResponse(org.mockito.Mockito.any())).thenAnswer(call -> {
                Mission mission = call.getArgument(0);
                return com.ondemandmonitoring.mission.dto.response.MissionResponse.builder().id(mission.getId())
                        .missionCode(mission.getMissionCode()).status(mission.getStatus()).build();
            });
            return new MissionService(missions, null, null, mapper, null,
                    null, null, null, null, null,
                    orders, OrderMissionPostgresTest.paidInvoices(), OrderMissionPostgresTest.paidEligibility(), null, null, null, null, null, null, actor, null, null, null, null, executions,
                    org.mockito.Mockito.mock(com.ondemandmonitoring.mission.service.IMissionChecklistAssignmentService.class),
                    org.mockito.Mockito.mock(IResourceReservationService.class), null);
        }
        @Bean IMissionCancellationService cancellation(OrderRepository orders,MissionRepository missions,
                MissionResultRepository results,AuthenticatedUserResolver actor) {
            return new MissionCancellationServiceImpl(orders,missions,results,actor);
        }
    }
}
