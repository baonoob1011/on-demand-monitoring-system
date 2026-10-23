package com.ondemandmonitoring.checklist;

import com.ondemandmonitoring.common.exception.*;
import com.ondemandmonitoring.mission.domain.Mission;
import com.ondemandmonitoring.mission.dto.request.MissionCreateRequest;
import com.ondemandmonitoring.mission.dto.response.MissionCreationResult;
import com.ondemandmonitoring.mission.repository.MissionRepository;
import com.ondemandmonitoring.mission.service.IMissionService;
import com.ondemandmonitoring.order.enums.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import static org.junit.jupiter.api.Assertions.*;

/** Opt-in actual PostgreSQL, random schema, real Order lock and transaction boundaries. */
@SpringJUnitConfig(OrderMissionPostgresTest.MissionConfig.class)
class MultiMissionCreationPostgresTest extends ChecklistPostgresTest {
    @Autowired MissionRepository missions;
    @Autowired IMissionService missionService;
    JdbcTemplate jdbc;
    com.ondemandmonitoring.order.dto.request.OrderCreateRequest baseRequest;
    @Override @BeforeEach void prepare() {
        jdbc=new JdbcTemplate(dataSource); jdbc.execute("TRUNCATE missions CASCADE");
        super.prepare(); baseRequest=null;
    }
    String order() {
        // Reuse one identity/time fixture when creating another Order in the same test.
        var s=baseRequest==null ? service("Monitoring") : baseRequest.getServiceId();
        if(baseRequest==null) baseRequest=orderRequest(s);
        baseRequest.setServiceId(s);
        var created=orderService.createOrder(baseRequest);
        transaction.executeWithoutResult(tx->orders.findById(created.getId()).orElseThrow().setOrderStatus(OrderStatus.APPROVED));
        return created.getId();
    }
    MissionCreateRequest request(String order,String key,long revision) {
        var r=new MissionCreateRequest(); r.setOrderId(order); r.setIdempotencyKey(key); r.setExpectedPlanRevision(revision);
        r.setScopeLabel("Whole Order scope");
        var start=java.time.LocalDate.now().plusDays(7).atStartOfDay(java.time.ZoneOffset.UTC).toInstant();
        r.setScheduledStartAt(start); r.setScheduledEndAt(start.plusSeconds(3600)); return r;
    }
    long revision(String order) {return jdbc.queryForObject("select execution_plan_revision from orders where id=?",Long.class,order);}
    long count(String order) {return jdbc.queryForObject("select count(*) from missions where order_id=?",Long.class,order);}
    @Test void multipleIntentsAndExactReplayPersistWithoutAnyExecutions() {
        String o=order(),key=UUID.randomUUID().toString();
        var first=missionService.createMissionWithOutcome(request(o,key,0)); assertTrue(first.created());
        var replay=missionService.createMissionWithOutcome(request(o,key,999)); assertFalse(replay.created());
        assertEquals(first.mission().getId(),replay.mission().getId()); assertEquals(1,revision(o));
        var second=missionService.createMissionWithOutcome(request(o,UUID.randomUUID().toString(),1));
        assertNotEquals(first.mission().getId(),second.mission().getId()); assertEquals(2,count(o)); assertEquals(2,revision(o));
        assertEquals(0,jdbc.queryForObject("select count(*) from mission_checklist_executions",Integer.class));
        var children=missions.findAllByOrderIdOrderByScheduledStartAtAscIdAsc(o);
        assertEquals(2,children.size()); assertTrue(children.get(0).getId().compareTo(children.get(1).getId())<0);
        assertTrue(children.stream().allMatch(m->m.getCreationRequestHash().length()==64 && m.getTargetPoint()!=null));
    }
    @Test void sameKeyDifferentPayloadConflictsAndFinalizedReplayStillWorks() {
        String o=order(),key=UUID.randomUUID().toString(); var r=request(o,key,0);
        var first=missionService.createMissionWithOutcome(r);
        transaction.executeWithoutResult(tx->{var order=orders.findById(o).orElseThrow(); order.setExecutionPlanStatus(ExecutionPlanStatus.FINALIZED); order.setExecutionPlanFinalizedAt(java.time.Instant.now()); order.setExecutionPlanFinalizedBy("test-manager"); order.setOrderStatus(OrderStatus.COMPLETED);});
        assertEquals(first.mission().getId(),missionService.createMissionWithOutcome(r).mission().getId());
        r.setScopeLabel("Changed intent");
        assertEquals(ErrorCode.IDEMPOTENCY_KEY_REUSED,assertThrows(ApiException.class,()->missionService.createMission(r)).getErrorCode());
        assertEquals(1,count(o)); assertEquals(1,revision(o));
    }
    @Test void differentOrdersCanReuseSameKey() {
        String key=UUID.randomUUID().toString(),a=order(),b=order();
        assertNotEquals(missionService.createMission(request(a,key,0)).getId(),missionService.createMission(request(b,key,0)).getId());
        assertEquals(1,count(a)); assertEquals(1,count(b));
    }
    @Test void databaseEnforcesPairUniquenessAndKeyHashConsistency() {
        String o=order(),key=UUID.randomUUID().toString(); missionService.createMission(request(o,key,0));
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->jdbc.update("insert into missions(id,mission_code,order_id,status,scheduled_start_at,scheduled_end_at,created_at,version,creation_idempotency_key,creation_request_hash,flight_permit_status) select 'duplicate','duplicate',order_id,status,scheduled_start_at,scheduled_end_at,created_at,version,creation_idempotency_key,creation_request_hash,flight_permit_status from missions where order_id=?",o));
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->jdbc.update("update missions set creation_request_hash=null where order_id=?",o));
    }
    @Test void failedInsertRollsBackPlanRevisionAtomically() {
        String o=order(); var request=request(o,UUID.randomUUID().toString(),0);
        jdbc.execute("alter table missions add constraint fixture_reject_new check (creation_idempotency_key is null)");
        try {assertThrows(jakarta.persistence.PersistenceException.class,()->missionService.createMission(request)); assertEquals(0,revision(o)); assertEquals(0,count(o));}
        finally {jdbc.execute("alter table missions drop constraint fixture_reject_new");}
        var retry=missionService.createMissionWithOutcome(request);
        assertTrue(retry.created()); assertEquals(1,count(o)); assertEquals(1,revision(o));
        assertEquals(retry.mission().getId(),missionService.createMissionWithOutcome(request).mission().getId());
        assertEquals(1,count(o)); assertEquals(1,revision(o));
    }
    List<Object> concurrent(MissionCreateRequest a,MissionCreateRequest b) throws Exception {
        var locked=new CountDownLatch(1); var release=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(3)) {
            var holder=pool.submit(()->transaction.executeWithoutResult(tx->{orders.findByIdForUpdate(a.getOrderId()).orElseThrow();locked.countDown();try {assertTrue(release.await(10,TimeUnit.SECONDS));}catch(InterruptedException e){throw new IllegalStateException(e);}}));
            assertTrue(locked.await(5,TimeUnit.SECONDS));
            Callable<Object> first=()->{try{return missionService.createMissionWithOutcome(a);}catch(ApiException e){return e.getErrorCode();}};
            Callable<Object> second=()->{try{return missionService.createMissionWithOutcome(b);}catch(ApiException e){return e.getErrorCode();}};
            var f=pool.submit(first);var s=pool.submit(second);
            long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);int waiting=0;
            while(System.nanoTime()<deadline&&waiting<2){waiting=jdbc.queryForObject("select count(*) from pg_stat_activity where datname=current_database() and usename=current_user and wait_event_type='Lock' and cardinality(pg_blocking_pids(pid)) > 0",Integer.class);Thread.sleep(20);}
            assertEquals(2,waiting); release.countDown();holder.get(10,TimeUnit.SECONDS);
            return List.of(f.get(10,TimeUnit.SECONDS),s.get(10,TimeUnit.SECONDS));
        } finally {release.countDown();}
    }
    @Test void concurrentSameKeyCreatesExactlyOnceAndReplaysAfterRevisionChange() throws Exception {
        String o=order(),key=UUID.randomUUID().toString(); var outcomes=concurrent(request(o,key,0),request(o,key,0));
        var a=(MissionCreationResult)outcomes.getFirst();var b=(MissionCreationResult)outcomes.getLast();
        assertNotEquals(a.created(),b.created());assertEquals(a.mission().getId(),b.mission().getId());assertEquals(1,count(o));assertEquals(1,revision(o));
    }
    @Test void concurrentDifferentKeysOneStaleThenRetrySameKeyWithRefreshedRevision() throws Exception {
        String o=order();var a=request(o,UUID.randomUUID().toString(),0);var b=request(o,UUID.randomUUID().toString(),0);
        var outcomes=concurrent(a,b);assertEquals(1,outcomes.stream().filter(MissionCreationResult.class::isInstance).count());
        assertEquals(1,outcomes.stream().filter(ErrorCode.EXECUTION_PLAN_REVISION_CONFLICT::equals).count());
        var stale=outcomes.getFirst() instanceof ErrorCode?a:b;stale.setExpectedPlanRevision(revision(o));
        assertTrue(missionService.createMissionWithOutcome(stale).created());assertEquals(2,count(o));assertEquals(2,revision(o));
    }
}
