package com.ondemandmonitoring.mission.service.impl;

import com.ondemandmonitoring.common.exception.*;
import com.ondemandmonitoring.media.domain.*;
import com.ondemandmonitoring.media.repository.MediaAssetRepository;
import com.ondemandmonitoring.media.service.IMediaObjectStorage;
import com.ondemandmonitoring.mission.domain.*;
import com.ondemandmonitoring.mission.dto.request.*;
import com.ondemandmonitoring.mission.dto.response.*;
import com.ondemandmonitoring.mission.enums.*;
import com.ondemandmonitoring.mission.mapper.ChecklistEvidenceMapper;
import com.ondemandmonitoring.mission.repository.*;
import com.ondemandmonitoring.mission.service.*;
import com.ondemandmonitoring.user.service.AuthenticatedUserResolver;
import java.time.Instant;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChecklistEvidenceServiceImpl implements IChecklistEvidenceService {
    private final MissionRepository missions;
    private final MissionChecklistExecutionRepository executions;
    private final MissionChecklistEvidenceRepository evidence;
    private final MediaAssetRepository media;
    private final MissionResultRepository results;
    private final IMissionAuthorizationService authorization;
    private final AuthenticatedUserResolver currentUser;
    private final ChecklistEvidencePolicy policy;
    private final ChecklistEvidenceMapper mapper;
    private final IMediaObjectStorage storage;

    @Override
    @Transactional
    public List<ChecklistEvidenceResponse> attach(String missionId, BatchChecklistEvidenceRequest request) {
        Mission mission = mission(missionId, true);
        if (!authorization.canViewMission(missionId)) throw new ApiException(ErrorCode.ACCESS_DENIED);
        editable(missionId);
        if (!authorization.canAttachChecklistEvidence(missionId)) throw new ApiException(ErrorCode.ACCESS_DENIED);
        if (request == null || request.mediaId() == null || request.mediaId().isBlank() || request.targets() == null
                || request.targets().isEmpty() || request.targets().size() > 100
                || request.targets().stream().anyMatch(t -> t == null || t.executionId() == null || t.executionId().isBlank()
                        || t.expectedVersion() == null || t.expectedVersion() < 0))
            throw new ApiException(ErrorCode.INVALID_REQUEST);
        MediaAsset asset = media.findByIdForUpdate(request.mediaId()).orElseThrow(() -> new ApiException(ErrorCode.MEDIA_NOT_FOUND));
        if (!missionId.equals(asset.getMissionId())) throw new ApiException(ErrorCode.ACCESS_DENIED);
        if (!policy.attachable(asset)) throw new ApiException(ErrorCode.EVIDENCE_NOT_ELIGIBLE, policy.ineligibilityReason(asset, false));
        var response = new ArrayList<ChecklistEvidenceResponse>();
        var seen = new HashSet<String>();
        // Mission lock serializes all same-mission commands; the SQL partial index remains the final boundary.
        for (var target : request.targets().stream().sorted(Comparator.comparing(BatchChecklistEvidenceRequest.Target::executionId)).toList()) {
            if (!seen.add(target.executionId())) throw new ApiException(ErrorCode.INVALID_REQUEST, "Duplicate execution target");
            MissionChecklistExecution execution = execution(mission, target.executionId());
            var existing = evidence.findByMissionChecklistExecution_IdAndMediaAsset_IdAndDetachedAtIsNull(execution.getId(), asset.getId());
            if (existing.isPresent()) { response.add(toResponse(existing.get())); continue; }
            version(execution, target.expectedVersion());
            var link = new MissionChecklistEvidence();
            link.setMissionId(missionId); link.setMissionChecklistExecution(execution); link.setMediaAsset(asset);
            link.setAttachedBy(currentUser.getCurrentUserId()); link.setAttachedAt(Instant.now());
            link.setNote(trimmed(request.note()));
            response.add(toResponse(evidence.saveAndFlush(link)));
            touch(execution);
        }
        return response;
    }

    @Override
    @Transactional
    public void detach(String missionId, String executionId, String evidenceId, long expectedVersion, String reason) {
        Mission mission = mission(missionId, true); editable(missionId);
        if (!authorization.canViewMission(missionId)) throw new ApiException(ErrorCode.ACCESS_DENIED);
        if (!authorization.canDetachChecklistEvidence(missionId)) throw new ApiException(ErrorCode.ACCESS_DENIED);
        MissionChecklistExecution execution = execution(mission, executionId);
        var link = evidence.findById(evidenceId).orElseThrow(() -> new ApiException(ErrorCode.EVIDENCE_NOT_FOUND));
        if (!missionId.equals(link.getMissionId()) || !executionId.equals(link.getMissionChecklistExecution().getId()))
            throw new ApiException(ErrorCode.ACCESS_DENIED);
        media.findByIdForUpdate(link.getMediaAsset().getId()).orElseThrow(() -> new ApiException(ErrorCode.MEDIA_NOT_FOUND));
        if (link.getDetachedAt() != null) return;
        version(execution, expectedVersion);
        link.setDetachedBy(currentUser.getCurrentUserId()); link.setDetachedAt(Instant.now());
        link.setDetachReason(trimmed(reason)); evidence.saveAndFlush(link); touch(execution);
    }

    @Override
    public List<ChecklistEvidenceCandidate> candidates(String missionId, int page, int size) {
        if (page < 0 || size < 1 || size > 100) throw new ApiException(ErrorCode.INVALID_REQUEST);
        mission(missionId, false);
        if (!authorization.canViewMission(missionId)) throw new ApiException(ErrorCode.ACCESS_DENIED);
        var links = active(missionId);
        return media.findMissionCandidates(missionId, PageRequest.of(page, size)).stream().map(asset -> {
            String url = preview(asset);
            return new ChecklistEvidenceCandidate(asset.getId(), asset.getOriginalFileName(), asset.getType(), asset.getContentType(),
                    asset.getMediaStatus(), asset.getSourceType(), asset.getCapturedAt(), asset.getValidatedAt(), policy.attachable(asset),
                    policy.ineligibilityReason(asset, false) == null, policy.ineligibilityReason(asset, true) == null,
                    policy.ineligibilityReason(asset, false), url, url == null ? null : Instant.now().plusSeconds(storage.presignedUrlExpiresSeconds()),
                    links.stream().filter(e -> asset.getId().equals(e.getMediaAsset().getId())).map(e -> e.getMissionChecklistExecution().getId()).toList());
        }).toList();
    }

    @Override
    public List<MissionChecklistEvidence> active(String missionId) {
        var links = evidence.findActiveByMissionId(missionId);
        if (links.stream().anyMatch(link -> !missionId.equals(link.getMissionChecklistExecution().getMissionId())
                || !missionId.equals(link.getMediaAsset().getMissionId())))
            throw new ApiException(ErrorCode.CHECKLIST_EXECUTION_INTEGRITY_INVALID);
        return links;
    }

    @Override
    public boolean ready(MissionChecklistExecution execution, List<MissionChecklistEvidence> links, boolean finalApproval) {
        if (execution.getExecutionStatus() == ChecklistExecutionStatus.UNABLE_TO_VERIFY)
            return execution.getUnableToVerifyReason() != null && !execution.getUnableToVerifyReason().isBlank();
        if (execution.getExecutionStatus() != ChecklistExecutionStatus.COMPLETED) return false;
        var item = execution.getOrderChecklistItem();
        if (item.getEvidencePolicyVersion() == 0) return item.getMinimumEvidenceCount() == 0;
        if (item.getEvidencePolicyVersion() != 1 || item.getMinimumEvidenceCount() != 1) return false;
        var applicable = links.stream().filter(e -> e.getDetachedAt() == null && execution.getId().equals(e.getMissionChecklistExecution().getId())).toList();
        long count = applicable.stream().filter(e -> policy.ineligibilityReason(e.getMediaAsset(), finalApproval) == null)
                .map(e -> e.getMediaAsset().getId()).distinct().count();
        // Required V1 packages cannot retain rejected/pending extra evidence at final approval.
        return count >= item.getMinimumEvidenceCount() && (!finalApproval || applicable.stream().allMatch(e -> policy.ineligibilityReason(e.getMediaAsset(), true) == null));
    }

    @Override
    public ChecklistExecutionResponse enrich(ChecklistExecutionResponse response, MissionChecklistExecution execution, List<MissionChecklistEvidence> links) {
        var applicable = links.stream().filter(e -> execution.getId().equals(e.getMissionChecklistExecution().getId()) && e.getDetachedAt() == null).toList();
        response.setEvidencePolicyVersion(execution.getOrderChecklistItem().getEvidencePolicyVersion());
        response.setMinimumEvidenceCount(execution.getOrderChecklistItem().getMinimumEvidenceCount());
        response.setEvidence(applicable.stream().map(this::toResponse).toList());
        response.setEligibleEvidenceCount((int) applicable.stream().filter(e -> policy.ineligibilityReason(e.getMediaAsset(), false) == null).map(e -> e.getMediaAsset().getId()).distinct().count());
        response.setEvidenceRequirementSatisfied(execution.getExecutionStatus() == ChecklistExecutionStatus.UNABLE_TO_VERIFY
                || response.getEligibleEvidenceCount() >= response.getMinimumEvidenceCount());
        response.setEvidenceReady(ready(execution, links, false));
        var blockers = new LinkedHashSet<String>();
        if (!execution.getExecutionStatus().isTerminal()) blockers.add("EXECUTION_NOT_TERMINAL");
        if (!response.isEvidenceRequirementSatisfied()) {
            blockers.add("INSUFFICIENT_EVIDENCE");
            applicable.stream().map(e -> policy.ineligibilityReason(e.getMediaAsset(), false)).filter(Objects::nonNull).forEach(blockers::add);
        }
        if (execution.getExecutionStatus() == ChecklistExecutionStatus.UNABLE_TO_VERIFY && !response.isEvidenceReady()) blockers.add("UNABLE_REASON_REQUIRED");
        response.setBlockingReasons(List.copyOf(blockers)); return response;
    }

    private ChecklistEvidenceResponse toResponse(MissionChecklistEvidence link) {
        var response = mapper.toResponse(link); var asset = link.getMediaAsset();
        response.setEligibleForOperationalReadiness(policy.ineligibilityReason(asset, false) == null);
        response.setEligibleForFinalApproval(policy.ineligibilityReason(asset, true) == null);
        response.setIneligibilityReason(policy.ineligibilityReason(asset, false));
        response.setPreviewUrl(preview(asset));
        if (response.getPreviewUrl() != null) response.setUrlExpiresAt(Instant.now().plusSeconds(storage.presignedUrlExpiresSeconds()));
        return response;
    }
    private String preview(MediaAsset asset) {
        // Never sign upload-pending/nonvalidated objects for evidence previews.
        if (asset.getValidatedAt() == null || asset.getMediaStatus() == null
                || !Set.of(MediaStatus.PENDING_MANAGER_APPROVAL, MediaStatus.AVAILABLE, MediaStatus.REJECTED).contains(asset.getMediaStatus())) return null;
        return storage.createPresignedGetUrl(asset.getS3Bucket(), asset.getS3Key());
    }
    private Mission mission(String id, boolean lock) {
        return (lock ? missions.findByIdForUpdate(id) : missions.findById(id)).orElseThrow(() -> new ApiException(ErrorCode.MISSION_NOT_FOUND));
    }
    private MissionChecklistExecution execution(Mission mission, String id) {
        var execution = executions.findById(id).orElseThrow(() -> new ApiException(ErrorCode.CHECKLIST_EXECUTION_NOT_FOUND));
        if (!mission.getId().equals(execution.getMissionId())) throw new ApiException(ErrorCode.CHECKLIST_EXECUTION_PARENT_MISMATCH);
        if (!mission.getOrder().getId().equals(execution.getOrderId()) || execution.getOrderChecklistItem() == null
                || !mission.getOrder().getId().equals(execution.getOrderChecklistItem().getOrder().getId()))
            throw new ApiException(ErrorCode.CHECKLIST_EXECUTION_INTEGRITY_INVALID);
        return execution;
    }
    private void editable(String missionId) {
        if (results.findByMissionId(missionId).map(r -> r.getApprovalStatus() == MissionResultApprovalStatus.APPROVED
                || r.getApprovalStatus() == MissionResultApprovalStatus.PENDING_MANAGER_APPROVAL).orElse(false))
            throw new ApiException(ErrorCode.CHECKLIST_EXECUTION_LOCKED);
    }
    private void version(MissionChecklistExecution execution, Long expected) {
        if (expected == null || expected < 0 || !Objects.equals(expected, execution.getVersion())) throw new ApiException(ErrorCode.CONCURRENT_UPDATE);
    }
    private void touch(MissionChecklistExecution execution) {
        execution.setUpdatedAt(Instant.now()); execution.setLastModifiedBy(currentUser.getCurrentUserId()); executions.saveAndFlush(execution);
    }
    private String trimmed(String value) {
        if (value == null) return null;
        if (value.length() > 1000) throw new ApiException(ErrorCode.INVALID_REQUEST);
        return value.isBlank() ? null : value.strip();
    }
}
