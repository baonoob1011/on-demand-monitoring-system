package com.ondemandmonitoring.checklist;

import com.ondemandmonitoring.common.exception.*;
import com.ondemandmonitoring.media.domain.MediaStatus;
import com.ondemandmonitoring.media.mapper.MediaWorkflowMapper;
import com.ondemandmonitoring.media.repository.*;
import com.ondemandmonitoring.media.service.*;
import com.ondemandmonitoring.media.service.impl.*;
import com.ondemandmonitoring.mission.domain.Mission;
import com.ondemandmonitoring.mission.dto.request.*;
import com.ondemandmonitoring.mission.dto.response.*;
import com.ondemandmonitoring.mission.enums.*;
import com.ondemandmonitoring.mission.repository.*;
import com.ondemandmonitoring.mission.service.*;
import com.ondemandmonitoring.order.enums.OrderStatus;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import jakarta.persistence.EntityManagerFactory;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Actual evidence/readiness/result/media-review services, JPA versions and real PostgreSQL transactions. */
@SpringJUnitConfig(ChecklistEvidenceIntegrationPostgresTest.EvidenceConfig.class)
class ChecklistEvidenceIntegrationPostgresTest extends OrderMissionPostgresTest {
    @Autowired IChecklistEvidenceService evidenceService;
    @Autowired IMissionChecklistExecutionService executionService;
    @Autowired IMissionAuthorizationService auth;
    @Autowired IMissionResultService resultService;
    @Autowired IManagerMediaApprovalService approvals;
    @Autowired MediaAssetRepository mediaRepository;
    @Override @BeforeEach void prepare() {
        super.prepare(); reset(auth);
        when(auth.canViewMission(anyString())).thenReturn(true); when(auth.canExecuteMonitoringChecklist(anyString())).thenReturn(true);
        when(auth.canAttachChecklistEvidence(anyString())).thenReturn(true); when(auth.canDetachChecklistEvidence(anyString())).thenReturn(true);
    }
    String mission(int count) {
        var request=orderRequest(service("Evidence service")); request.setChecklistItems(java.util.stream.IntStream.range(0,count).mapToObj(i->selected(null,"Historical evidence item "+i)).toList());
        String order=orderService.createOrder(request).getId();
        String actorId = resolver.getCurrentUser().getId();
        when(resolver.getCurrentUserId()).thenReturn(actorId);
        transaction.executeWithoutResult(tx->orders.findById(order).orElseThrow().setOrderStatus(OrderStatus.APPROVED));
        String mission=missionService.createMission(request(order)).getId(); setStatus(mission,MissionStatus.PENDING_REVIEW);return mission;
    }
    void setStatus(String mission,MissionStatus status) { transaction.executeWithoutResult(tx->{Mission entity=missions.findByIdForUpdate(mission).orElseThrow();entity.setStatus(status);if(status==MissionStatus.COMPLETED)entity.setCompletedAt(java.time.Instant.now());}); }
    String asset(String mission,MediaStatus status) {
        String id=UUID.randomUUID().toString(),device=UUID.randomUUID().toString(),model=UUID.randomUUID().toString();var jdbc=new JdbcTemplate(dataSource);
        jdbc.update("insert into device_models(id,created_at,version,code,model_name) values (?,now(),0,?,'Test model')",model,model);
        jdbc.update("insert into devices(id,created_at,version,serial_number,name,model_id,status) values (?,now(),0,?,'Test camera',?,'AVAILABLE')",device,device,model);
        jdbc.update("insert into mission_device_assignments(id,created_at,version,mission_id,device_id,device_role,checkup_status,is_current) values (?,now(),0,?,?,'MAIN','PENDING',true)",UUID.randomUUID().toString(),mission,device);
        jdbc.update("insert into drone_media(id,created_at,version,mission_id,device_id,media_type,storage_provider,original_file_name,content_type,file_size,s3_bucket,s3_key,s3_url,captured_at,media_status,source_type,validated_at) values (?,now(),0,?,?,'IMAGE','S3','capture.jpg','image/jpeg',20,'test',?,?,now(),?,'DRONE_CAMERA',now())",id,mission,device,id,"s3://test/"+id,status.name());return id;
    }
    ChecklistExecutionResponse row(String mission) {return executionService.getByMissionId(mission).executions().getFirst();}
    void complete(String mission) {
        var row=row(mission);var request=new ChecklistExecutionUpdateRequest();request.setExpectedVersion(row.getVersion());request.setExecutionStatus(ChecklistExecutionStatus.COMPLETED);request.setAssessmentStatus(ChecklistAssessmentStatus.NON_COMPLIANT);
        executionService.update(mission,row.getId(),request);
    }
    BatchChecklistEvidenceRequest attachRequest(String mission,String asset) {var row=row(mission);return new BatchChecklistEvidenceRequest(asset,List.of(new BatchChecklistEvidenceRequest.Target(row.getId(),row.getVersion())),null);}
    @Test void newSnapshotOperationalSubmissionAndFinalApprovalUseActualEvidence() {
        String mission=mission(1); assertEquals(1,row(mission).getEvidencePolicyVersion());complete(mission);
        assertFalse(executionService.getByMissionId(mission).checklistEvidenceReady());String media=asset(mission,MediaStatus.PENDING_MANAGER_APPROVAL);
        evidenceService.attach(mission,attachRequest(mission,media));assertTrue(executionService.getByMissionId(mission).readyForMissionCompletion());
        setStatus(mission,MissionStatus.COMPLETED);var result=resultService.upsert(mission,new MissionResultRequest());
        assertEquals(ErrorCode.CHECKLIST_NOT_READY,assertThrows(ApiException.class,()->resultService.approve(result.getId(),new MissionResultReviewRequest())).getErrorCode());
        approvals.approve(mission,media);resultService.approve(result.getId(),new MissionResultReviewRequest());
        assertThrows(ApiException.class,()->evidenceService.attach(mission,attachRequest(mission,media)));
        assertEquals(ErrorCode.CHECKLIST_EXECUTION_LOCKED, assertThrows(ApiException.class, () -> approvals.reject(mission, media)).getErrorCode());
    }
    @Test void batchIsAtomicAndSameMediaSupportsManyExecutions() {
        String mission=mission(2),media=asset(mission,MediaStatus.PENDING_MANAGER_APPROVAL);var rows=executionService.getByMissionId(mission).executions();
        var bad=new BatchChecklistEvidenceRequest(media,List.of(new BatchChecklistEvidenceRequest.Target(rows.getFirst().getId(),0L),new BatchChecklistEvidenceRequest.Target(rows.getLast().getId(),9L)),null);
        assertThrows(ApiException.class,()->evidenceService.attach(mission,bad));assertEquals(0,new JdbcTemplate(dataSource).queryForObject("select count(*) from mission_checklist_evidence",Integer.class));
        assertEquals(2,evidenceService.attach(mission,new BatchChecklistEvidenceRequest(media,rows.stream().map(r->new BatchChecklistEvidenceRequest.Target(r.getId(),r.getVersion())).toList(),null)).size());
    }
    @Test void concurrentDuplicateAttachIsIdempotentAndConsumesOneVersion() throws Exception {
        String mission=mission(1),media=asset(mission,MediaStatus.PENDING_MANAGER_APPROVAL);var request=attachRequest(mission,media);
        var outcomes=race(()->evidenceService.attach(mission,request));assertTrue(outcomes.stream().allMatch(List.class::isInstance));
        assertEquals(1,row(mission).getEvidence().size());assertEquals(1L,row(mission).getVersion());
    }
    @Test void rejectionInvalidatesSubmittedPackageAndExplicitResultRejectReopens() throws Exception {
        String mission=mission(1),media=asset(mission,MediaStatus.PENDING_MANAGER_APPROVAL);complete(mission);evidenceService.attach(mission,attachRequest(mission,media));setStatus(mission,MissionStatus.COMPLETED);
        var calls=new AtomicInteger();race(()->calls.getAndIncrement()==0?resultService.upsert(mission,new MissionResultRequest()):approvals.reject(mission,media));
        assertFalse(executionService.getByMissionId(mission).checklistEvidenceReady());
        var jdbc=new JdbcTemplate(dataSource);var pending=jdbc.queryForList("select id from mission_results where mission_id=? and approval_status='PENDING_MANAGER_APPROVAL'",String.class,mission);
        if(!pending.isEmpty()) {assertThrows(ApiException.class,()->evidenceService.detach(mission,row(mission).getId(),row(mission).getEvidence().getFirst().getEvidenceId(),row(mission).getVersion(),null));resultService.reject(pending.getFirst(),new MissionResultReviewRequest());}
        var current=row(mission);evidenceService.detach(mission,current.getId(),current.getEvidence().getFirst().getEvidenceId(),current.getVersion(),"replace");assertTrue(row(mission).getEvidence().isEmpty());
    }
    @Test void detachAndSubmitSerializeWithoutSilentlyApprovingIncompletePackage() throws Exception {
        String mission=mission(1),media=asset(mission,MediaStatus.PENDING_MANAGER_APPROVAL);complete(mission);evidenceService.attach(mission,attachRequest(mission,media));setStatus(mission,MissionStatus.COMPLETED);
        var row=row(mission);var calls=new AtomicInteger();var outcomes=race(()->{if(calls.getAndIncrement()==0)return resultService.upsert(mission,new MissionResultRequest());evidenceService.detach(mission,row.getId(),row.getEvidence().getFirst().getEvidenceId(),row.getVersion(),null);return "detached";});
        assertEquals(1,outcomes.stream().filter(ApiException.class::isInstance).count());
    }
    @Test void mediaApprovalAndFinalResultApprovalSerialize() throws Exception {
        String mission=mission(1),media=asset(mission,MediaStatus.PENDING_MANAGER_APPROVAL);complete(mission);evidenceService.attach(mission,attachRequest(mission,media));setStatus(mission,MissionStatus.COMPLETED);
        var result=resultService.upsert(mission,new MissionResultRequest());var calls=new AtomicInteger();
        var outcomes=race(()->calls.getAndIncrement()==0?approvals.approve(mission,media):resultService.approve(result.getId(),new MissionResultReviewRequest()));
        assertTrue(outcomes.stream().allMatch(r->!(r instanceof Throwable)||r instanceof ApiException e&&e.getErrorCode()==ErrorCode.CHECKLIST_NOT_READY));
        assertEquals(MediaStatus.AVAILABLE,mediaRepository.findById(media).orElseThrow().getMediaStatus());
    }
    @Configuration @Import(MissionChecklistPostgresTest.ExecutionConfig.class)
    static class EvidenceConfig {
        @Bean MediaNotificationOutboxRepository outbox(EntityManagerFactory factory) {return new JpaRepositoryFactory(SharedEntityManagerCreator.createSharedEntityManager(factory)).getRepository(MediaNotificationOutboxRepository.class);}
        @Bean com.ondemandmonitoring.mission.service.IMissionMediaEvidenceGuard mediaLock(MediaAssetRepository media,MissionRepository missions,MissionResultRepository results,MissionChecklistEvidenceRepository evidence) {return new com.ondemandmonitoring.mission.service.impl.MissionMediaEvidenceGuardImpl(media,missions,results,evidence);}
        @Bean IManagerMediaApprovalService approvalService(MediaAssetRepository media,MediaNotificationOutboxRepository outbox,IMediaObjectStorage storage,com.ondemandmonitoring.mission.service.IMissionMediaEvidenceGuard lock) {
            return new ManagerMediaApprovalServiceImpl(media,outbox,storage,org.mapstruct.factory.Mappers.getMapper(MediaWorkflowMapper.class),lock);
        }
    }
}
