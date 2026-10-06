package com.ondemandmonitoring.checklist;

import com.ondemandmonitoring.common.exception.*;
import com.ondemandmonitoring.mission.domain.Mission;
import com.ondemandmonitoring.mission.dto.request.MissionCreateRequest;
import com.ondemandmonitoring.mission.dto.response.MissionCreationResult;
import com.ondemandmonitoring.mission.repository.MissionRepository;
import com.ondemandmonitoring.mission.service.IMissionService;
import com.ondemandmonitoring.order.enums.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
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
        super.prepare(); baseRequest=null; cutover();
    }
    void cutover() {
        try { jdbc.execute(new ClassPathResource("seeddata/migrations/enable_order_multi_mission_creation.sql")
                .getContentAsString(StandardCharsets.UTF_8).replace("public.",SCHEMA+".")); }
        catch(java.io.IOException e) {throw new IllegalStateException(e);}
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
        var start=java.time.LocalDate.now().plusDays(1).atStartOfDay(java.time.ZoneOffset.UTC).toInstant();
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
    @Test void legacyRowsAndHistoricalExecutionDataSurviveRepeatableCutover() {
        String o=order();
        jdbc.update("insert into missions(id,mission_code,order_id,status,scheduled_start_at,scheduled_end_at,created_at,version) values ('legacy','legacy',?,'RESOURCE_ASSIGNING',now(),now()+interval '1 hour',now(),0)",o);
        jdbc.execute("ALTER TABLE missions ADD CONSTRAINT unknown_local_order_unique UNIQUE(order_id)");
        jdbc.execute("CREATE UNIQUE INDEX standalone_order_unique ON missions(order_id) INCLUDE(id)");
        String before=jdbc.queryForObject("select row_to_json(m)::text from missions m where id='legacy'",String.class);
        cutover(); cutover();
        assertEquals(before,jdbc.queryForObject("select row_to_json(m)::text from missions m where id='legacy'",String.class));
        assertNull(missions.findById("legacy").orElseThrow().getCreationIdempotencyKey());
        assertEquals(ErrorCode.MULTI_MISSION_LEGACY_ORDER_NOT_MIGRATED,assertThrows(ApiException.class,
                ()->missionService.createMission(request(o,UUID.randomUUID().toString(),0))).getErrorCode());
        jdbc.update("insert into missions(id,mission_code,order_id,status,scheduled_start_at,scheduled_end_at,created_at,version) values ('second','second',?,'RESOURCE_ASSIGNING',now(),now()+interval '1 hour',now(),0)",o);
        assertEquals(2,count(o));
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->jdbc.update("update missions set order_id='missing' where id='second'"));
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->jdbc.update("update missions set order_id=null where id='second'"));
        assertEquals(1,jdbc.queryForObject("select count(*) from pg_constraint where conrelid='"+SCHEMA+".missions'::regclass and contype='u' and pg_get_constraintdef(oid)='UNIQUE (id, order_id)'",Integer.class));
    }
    @Test void databaseEnforcesPairUniquenessAndKeyHashConsistency() {
        String o=order(),key=UUID.randomUUID().toString(); missionService.createMission(request(o,key,0));
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->jdbc.update("insert into missions(id,mission_code,order_id,status,scheduled_start_at,scheduled_end_at,created_at,version,creation_idempotency_key,creation_request_hash) select 'duplicate','duplicate',order_id,status,scheduled_start_at,scheduled_end_at,created_at,version,creation_idempotency_key,creation_request_hash from missions where order_id=?",o));
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->jdbc.update("update missions set creation_request_hash=null where order_id=?",o));
    }
    @Test void migrationPreservesLegacyExecutionsEvidenceResultAndGlobalExecutionUnique() {
        var s=service("Historical monitoring");var r=orderRequest(s);r.setChecklistItems(List.of(selected(null,"Historical requirement")));
        String o=orderService.createOrder(r).getId();String item=snapshots.findAllByOrderIdInOrderByDisplayOrderAscIdAsc(List.of(o)).getFirst().getId();
        jdbc.update("insert into missions(id,mission_code,order_id,status,scheduled_start_at,scheduled_end_at,created_at,version) values ('historical','historical',?,'COMPLETED',now(),now()+interval '1 hour',now(),0)",o);
        jdbc.update("insert into mission_checklist_executions(id,created_at,version,mission_id,order_id,order_checklist_item_id,execution_status,assessment_status,started_at,completed_at) values ('execution',now(),4,'historical',?,?,'COMPLETED','COMPLIANT',now(),now())",o,item);
        jdbc.execute("insert into device_models(id,created_at,version,code,model_name) values ('model',now(),0,'model','Test model')");
        jdbc.execute("insert into devices(id,created_at,version,serial_number,name,model_id,status) values ('device',now(),0,'device','Camera','model','AVAILABLE')");
        jdbc.execute("insert into mission_device_assignments(id,created_at,version,mission_id,device_id,device_role,checkup_status,is_current) values ('device-assignment',now(),0,'historical','device','MAIN','PENDING',true)");
        jdbc.execute("insert into drone_media(id,created_at,version,mission_id,device_id,media_type,storage_provider,original_file_name,content_type,file_size,s3_bucket,s3_key,s3_url,captured_at,media_status,source_type,validated_at) values ('media',now(),0,'historical','device','IMAGE','S3','photo.jpg','image/jpeg',20,'test','photo','s3://test/photo',now(),'AVAILABLE','DRONE_CAMERA',now())");
        jdbc.update("insert into mission_checklist_evidence(id,created_at,version,mission_id,mission_checklist_execution_id,media_asset_id,attached_by,attached_at) values ('evidence',now(),2,'historical','execution','media',?,now())",resolver.getCurrentUser().getId());
        jdbc.execute("insert into mission_results(id,created_at,version,mission_id,status,approval_status,completed_at,media_count,summary) values ('result',now(),3,'historical','COMPLETED','APPROVED',now(),1,'Historical result')");
        jdbc.execute("insert into mission_result_media(mission_result_id,media_id) values ('result','media')");
        var tables=List.of("missions","mission_checklist_executions","mission_checklist_evidence","mission_results","mission_result_media","drone_media");
        var before=new HashMap<String,List<String>>();
        for(String table:tables) before.put(table,jdbc.queryForList("select row_to_json(t)::text from "+table+" t",String.class));
        cutover();cutover();
        for(String table:tables) assertEquals(before.get(table),jdbc.queryForList("select row_to_json(t)::text from "+table+" t",String.class),table);
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->jdbc.update("insert into mission_checklist_executions(id,created_at,version,mission_id,order_id,order_checklist_item_id,execution_status,assessment_status) values ('duplicate-execution',now(),0,'historical',?,?,'PENDING','NOT_ASSESSED')",o,item));
    }
    @Test void failedInsertRollsBackPlanRevisionAtomically() {
        String o=order(); jdbc.execute("alter table missions add constraint fixture_reject_new check (creation_idempotency_key is null)");
        try {assertThrows(jakarta.persistence.PersistenceException.class,()->missionService.createMission(request(o,UUID.randomUUID().toString(),0))); assertEquals(0,revision(o)); assertEquals(0,count(o));}
        finally {jdbc.execute("alter table missions drop constraint fixture_reject_new");}
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
            while(System.nanoTime()<deadline&&waiting<2){waiting=jdbc.queryForObject("select count(*) from pg_stat_activity where datname=current_database() and usename=current_user and wait_event_type='Lock' and query like '%orders%' and query like '%for%'",Integer.class);Thread.sleep(20);}
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
