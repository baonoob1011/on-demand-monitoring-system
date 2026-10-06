package com.ondemandmonitoring.mission.service;
import com.ondemandmonitoring.common.exception.*;
import com.ondemandmonitoring.media.domain.*;
import com.ondemandmonitoring.media.repository.MediaAssetRepository;
import com.ondemandmonitoring.media.service.IMediaObjectStorage;
import com.ondemandmonitoring.mission.domain.*;
import com.ondemandmonitoring.mission.dto.request.BatchChecklistEvidenceRequest;
import com.ondemandmonitoring.mission.dto.response.ChecklistExecutionResponse;
import com.ondemandmonitoring.mission.enums.*;
import com.ondemandmonitoring.mission.mapper.ChecklistEvidenceMapper;
import com.ondemandmonitoring.mission.repository.*;
import com.ondemandmonitoring.mission.service.impl.*;
import com.ondemandmonitoring.order.domain.Order;
import com.ondemandmonitoring.order.domain.OrderChecklistItem;
import com.ondemandmonitoring.user.service.AuthenticatedUserResolver;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ChecklistEvidenceServiceTest {
    MissionRepository missions = mock(MissionRepository.class);
    MissionChecklistExecutionRepository executions = mock(MissionChecklistExecutionRepository.class);
    MissionChecklistEvidenceRepository links = mock(MissionChecklistEvidenceRepository.class);
    MediaAssetRepository media = mock(MediaAssetRepository.class);
    MissionResultRepository results = mock(MissionResultRepository.class);
    IMissionAuthorizationService auth = mock(IMissionAuthorizationService.class);
    AuthenticatedUserResolver actor = mock(AuthenticatedUserResolver.class);
    IMediaObjectStorage storage = mock(IMediaObjectStorage.class);
    ChecklistEvidencePolicy policy = new ChecklistEvidencePolicy();
    ChecklistEvidenceServiceImpl service = new ChecklistEvidenceServiceImpl(missions, executions, links, media, results, auth, actor,
            policy, org.mapstruct.factory.Mappers.getMapper(ChecklistEvidenceMapper.class), storage);
    Mission mission; MissionChecklistExecution execution; MediaAsset asset;
    @BeforeEach void setup() {
        var order = new Order(); order.setId("o"); mission = new Mission(); mission.setId("m"); mission.setOrder(order); mission.setStatus(MissionStatus.PENDING_REVIEW);
        var item = new OrderChecklistItem(); item.setId("i"); item.setOrder(order); item.setEvidencePolicyVersion(1); item.setMinimumEvidenceCount(1);
        execution = new MissionChecklistExecution(); execution.setId("e"); execution.setMissionId("m"); execution.setOrderId("o"); execution.setOrderChecklistItem(item); execution.setVersion(0L);
        execution.setExecutionStatus(ChecklistExecutionStatus.COMPLETED);
        asset = new MediaAsset(); asset.setId("a"); asset.setMissionId("m"); asset.setType("IMAGE"); asset.setSourceType("DRONE_CAMERA");
        asset.setMediaStatus(MediaStatus.PENDING_MANAGER_APPROVAL); asset.setValidatedAt(Instant.now());
        when(missions.findByIdForUpdate("m")).thenReturn(Optional.of(mission)); when(executions.findById("e")).thenReturn(Optional.of(execution));
        when(media.findByIdForUpdate("a")).thenReturn(Optional.of(asset));
        when(auth.canAttachChecklistEvidence("m")).thenReturn(true); when(auth.canDetachChecklistEvidence("m")).thenReturn(true);
        when(auth.canViewMission("m")).thenReturn(true);
        when(actor.getCurrentUserId()).thenReturn("actor");
        when(links.saveAndFlush(any())).thenAnswer(call -> { MissionChecklistEvidence link = call.getArgument(0); if(link.getId()==null) link.setId(UUID.randomUUID().toString()); return link; });
    }
    BatchChecklistEvidenceRequest request() { return new BatchChecklistEvidenceRequest("a", List.of(new BatchChecklistEvidenceRequest.Target("e", 0L)), " note "); }
    MissionChecklistEvidence link() { var link=new MissionChecklistEvidence(); link.setId("link"); link.setMissionId("m"); link.setMissionChecklistExecution(execution); link.setMediaAsset(asset); link.setAttachedAt(Instant.now()); return link; }
    void error(ErrorCode code, Runnable command) { assertEquals(code, assertThrows(ApiException.class, command::run).getErrorCode()); }

    @Test void attachesStableMediaIdentityAndTouchesExecution() {
        var response=service.attach("m", request()); assertEquals("a", response.getFirst().getMediaId()); assertEquals("actor", response.getFirst().getAttachedBy());
        assertEquals("note", response.getFirst().getNote()); verify(executions).saveAndFlush(execution);
    }
    @Test void duplicateIsIdempotentEvenWithOldVersion() {
        when(links.findByMissionChecklistExecution_IdAndMediaAsset_IdAndDetachedAtIsNull("e","a")).thenReturn(Optional.of(link())); execution.setVersion(8L);
        assertEquals("link", service.attach("m",request()).getFirst().getEvidenceId()); verify(links,never()).saveAndFlush(any()); verify(executions,never()).saveAndFlush(any());
    }
    @Test void rejectsWrongMediaMission() { asset.setMissionId("other"); error(ErrorCode.ACCESS_DENIED,()->service.attach("m",request())); }
    @Test void rejectsWrongExecutionMission() { execution.setMissionId("other"); error(ErrorCode.CHECKLIST_EXECUTION_PARENT_MISMATCH,()->service.attach("m",request())); }
    @Test void rejectsStaleVersion() { execution.setVersion(1L); error(ErrorCode.CONCURRENT_UPDATE,()->service.attach("m",request())); }
    @Test void deniesUnassignedActor() { when(auth.canAttachChecklistEvidence("m")).thenReturn(false); error(ErrorCode.ACCESS_DENIED,()->service.attach("m",request())); }
    @Test void permitsSystemSnapshotsAsChecklistEvidence() {
        asset.setSourceType("SATELLITE_SNAPSHOT");

        assertEquals(1, service.attach("m", request()).size());
        assertTrue(service.ready(execution, List.of(link()), false));
    }
    @Test void rejectsManualUnknownAndMapillarySources() {
        for(String source:Arrays.asList("MANUAL_UPLOAD","MAPILLARY_REFERENCE",null)) { asset.setSourceType(source); error(ErrorCode.EVIDENCE_NOT_ELIGIBLE,()->service.attach("m",request())); }
    }
    @Test void permitsPreValidationLinkButDoesNotCount() {
        asset.setMediaStatus(MediaStatus.UPLOAD_PENDING); asset.setValidatedAt(null);
        assertEquals(1,service.attach("m",request()).size()); assertFalse(service.ready(execution,List.of(link()),false));
    }
    @Test void supportsOneMediaAcrossMultipleItems() {
        var second=new MissionChecklistExecution(); second.setId("e2"); second.setMissionId("m"); second.setOrderId("o"); second.setOrderChecklistItem(execution.getOrderChecklistItem()); second.setVersion(0L);
        when(executions.findById("e2")).thenReturn(Optional.of(second));
        assertEquals(2,service.attach("m",new BatchChecklistEvidenceRequest("a",List.of(new BatchChecklistEvidenceRequest.Target("e",0L),new BatchChecklistEvidenceRequest.Target("e2",0L)),null)).size());
    }
    @Test void softDetachPreservesHistoryAndReattachCreatesNewRow() {
        var old=link(); when(links.findById("link")).thenReturn(Optional.of(old)); service.detach("m","e","link",0L," replace ");
        assertNotNull(old.getDetachedAt()); assertEquals("actor",old.getDetachedBy()); assertEquals("replace",old.getDetachReason()); verify(links,never()).delete(any());
        assertNotEquals("link",service.attach("m",request()).getFirst().getEvidenceId());
    }
    @Test void pendingAndApprovedLockWhileRejectedReopens() {
        var result=new MissionResult(); when(results.findByMissionId("m")).thenReturn(Optional.of(result));
        for(var state:List.of(MissionResultApprovalStatus.PENDING_MANAGER_APPROVAL,MissionResultApprovalStatus.APPROVED)) { result.setApprovalStatus(state); error(ErrorCode.CHECKLIST_EXECUTION_LOCKED,()->service.attach("m",request())); }
        result.setApprovalStatus(MissionResultApprovalStatus.REJECTED); assertEquals(1,service.attach("m",request()).size());
    }
    @Test void readinessPendingOperationalAvailableFinalAndRejectionRecovery() {
        assertFalse(service.ready(execution,List.of(),false)); assertTrue(service.ready(execution,List.of(link()),false)); assertFalse(service.ready(execution,List.of(link()),true));
        asset.setMediaStatus(MediaStatus.AVAILABLE); assertTrue(service.ready(execution,List.of(link()),true));
        asset.setMediaStatus(MediaStatus.REJECTED); assertFalse(service.ready(execution,List.of(link()),false));
    }
    @ParameterizedTest @EnumSource(value=MediaStatus.class,names={"UPLOAD_PENDING","UPLOADING","VALIDATING","RETRY_REQUIRED","MANUAL_UPLOAD_REQUIRED","REJECTED"})
    void ineligibleStatesNeverCount(MediaStatus status) { asset.setMediaStatus(status); assertFalse(service.ready(execution,List.of(link()),false)); }
    @Test void unableReasonAndLegacyPolicyAreCompatible() {
        execution.setExecutionStatus(ChecklistExecutionStatus.UNABLE_TO_VERIFY); execution.setUnableToVerifyReason("Weather"); assertTrue(service.ready(execution,List.of(),true));
        execution.setUnableToVerifyReason(" "); assertFalse(service.ready(execution,List.of(),false));
        execution.setExecutionStatus(ChecklistExecutionStatus.COMPLETED); execution.getOrderChecklistItem().setEvidencePolicyVersion(0); execution.getOrderChecklistItem().setMinimumEvidenceCount(0);
        assertTrue(service.ready(execution,List.of(),true));
    }
    @Test void nonCompliantNotAssessedAndVideoRemainValid() {
        asset.setType("VIDEO"); execution.setAssessmentStatus(ChecklistAssessmentStatus.NON_COMPLIANT); assertTrue(service.ready(execution,List.of(link()),false));
        execution.setAssessmentStatus(ChecklistAssessmentStatus.NOT_ASSESSED); assertTrue(service.ready(execution,List.of(link()),false));
    }
    @Test void distinctMultipleMediaAndDetachedLinks() {
        var one=link(); var two=link(); assertTrue(service.ready(execution,List.of(one,two),false)); one.setDetachedAt(Instant.now()); two.setDetachedAt(Instant.now());
        assertFalse(service.ready(execution,List.of(one,two),false));
    }
    @Test void technicalValidationRequiredEvenWhenAvailable() { asset.setMediaStatus(MediaStatus.AVAILABLE); asset.setValidatedAt(null); assertFalse(service.ready(execution,List.of(link()),true)); }
}
