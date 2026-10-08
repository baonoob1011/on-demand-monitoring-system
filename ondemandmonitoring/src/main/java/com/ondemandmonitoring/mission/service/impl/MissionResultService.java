package com.ondemandmonitoring.mission.service.impl;

import com.ondemandmonitoring.common.api.PageResponse;
import com.ondemandmonitoring.common.exception.ApiException;
import com.ondemandmonitoring.common.exception.ErrorCode;
import com.ondemandmonitoring.delivery.service.IDeliveryWorkflowService;
import com.ondemandmonitoring.media.domain.MediaAsset;
import com.ondemandmonitoring.media.repository.MediaAssetRepository;
import com.ondemandmonitoring.media.service.IMediaAssetService;
import com.ondemandmonitoring.mission.domain.Mission;
import com.ondemandmonitoring.mission.domain.MissionResult;
import com.ondemandmonitoring.mission.dto.request.MissionResultRequest;
import com.ondemandmonitoring.mission.dto.request.MissionResultReviewRequest;
import com.ondemandmonitoring.mission.dto.response.MissionResultResponse;
import com.ondemandmonitoring.mission.enums.MissionResultApprovalStatus;
import com.ondemandmonitoring.mission.enums.MissionResultStatus;
import com.ondemandmonitoring.mission.enums.MissionStatus;
import com.ondemandmonitoring.mission.service.IMissionChecklistExecutionService;
import com.ondemandmonitoring.mission.service.IMissionAuthorizationService;
import com.ondemandmonitoring.user.service.AuthenticatedUserResolver;
import com.ondemandmonitoring.role.domain.RoleCode;
import com.ondemandmonitoring.mission.mapper.MissionResultMapper;
import com.ondemandmonitoring.mission.repository.MissionRepository;
import com.ondemandmonitoring.mission.repository.MissionResultRepository;
import com.ondemandmonitoring.order.domain.Order;
import com.ondemandmonitoring.order.repository.OrderRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.ArrayList;
import lombok.AccessLevel;
import org.springframework.security.access.prepost.PreAuthorize;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class MissionResultService implements com.ondemandmonitoring.mission.service.IMissionResultService {

    MissionResultRepository missionResultRepository;
    MissionRepository missionRepository;
    MediaAssetRepository mediaAssetRepository;
    IMediaAssetService mediaAssetService;
    MissionResultMapper missionResultMapper;
    /** Retained in the constructor for binary/test compatibility; result approval no longer completes an order. */
    OrderRepository orderRepository;
    IMissionChecklistExecutionService checklistExecutionService;
    AuthenticatedUserResolver currentUser;
    IMissionAuthorizationService authorization;
    IDeliveryWorkflowService deliveryWorkflow;

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("@missionAuthorizationService.canViewMission(#missionId)")
    public MissionResultResponse getByMissionId(String missionId) {
        return missionResultRepository.findByMissionId(missionId)
                .map(this::toResponse)
                .orElse(null);
    }

    @Override
    @Transactional
    @PreAuthorize("@missionAuthorizationService.canUploadMissionMedia(#missionId)")
    public MissionResultResponse upsert(String missionId, MissionResultRequest request) {
        Mission mission = missionRepository.findByIdForUpdate(missionId)
                .orElseThrow(() -> new ApiException(ErrorCode.MISSION_NOT_FOUND,
                        "Mission not found: " + missionId));
        if (!authorization.canUploadMissionMedia(missionId)) throw new ApiException(ErrorCode.ACCESS_DENIED);
        if (mission.getStatus() != MissionStatus.COMPLETED)
            throw new ApiException(ErrorCode.MISSION_STATUS_INVALID, "Complete operational mission before submitting results");
        checklistExecutionService.requireReadyForSubmission(mission);
        MissionResult result = missionResultRepository.findByMissionId(missionId)
                .orElseGet(() -> {
                    MissionResult created = new MissionResult();
                    created.setMission(mission);
                    created.setCreatedBy(currentUser.getCurrentUserId());
                    return created;
                });
        if (result.getApprovalStatus() == MissionResultApprovalStatus.APPROVED
                || result.getApprovalStatus() == MissionResultApprovalStatus.PENDING_MANAGER_APPROVAL)
            throw new ApiException(ErrorCode.CHECKLIST_EXECUTION_LOCKED);
        applyRequest(result, mission, request);
        return toResponse(missionResultRepository.save(result));
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public PageResponse<MissionResultResponse> listPendingManagerApproval(int page, int size) {
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "submittedAt", "id"));
        return PageResponse.from(missionResultRepository
                .findByApprovalStatus(MissionResultApprovalStatus.PENDING_MANAGER_APPROVAL, pageable)
                .map(this::toResponse));
    }

    @Override
    @Transactional
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public MissionResultResponse approve(String resultId, MissionResultReviewRequest request) {
        MissionResult result = requirePendingResult(resultId);
        checklistExecutionService.requireReadyForFinalApproval(result.getMission());
        if (result.getMission().getStatus() != MissionStatus.COMPLETED)
            throw new ApiException(ErrorCode.MISSION_STATUS_INVALID);
        boolean unfinishedMediaReview = mediaAssetRepository
                .findByMissionIdOrderByCapturedAtDesc(result.getMission().getId()).stream()
                .anyMatch(asset -> asset.getMediaStatus() != com.ondemandmonitoring.media.domain.MediaStatus.AVAILABLE
                        && asset.getMediaStatus() != com.ondemandmonitoring.media.domain.MediaStatus.REJECTED);
        if (unfinishedMediaReview) {
            throw new ApiException(ErrorCode.DELIVERY_NOT_READY,
                    "Approve or reject every mission media file before approving the mission result");
        }
        Instant now = Instant.now();
        result.setApprovalStatus(MissionResultApprovalStatus.APPROVED);
        result.setApprovedAt(now);
        result.setRejectedAt(null);
        result.setReviewedBy(currentUser.getCurrentUserId());
        result.setReviewNote(request == null ? null : request.getNote());
        attachMissionMedia(result, result.getMission().getId());
        MissionResult saved = missionResultRepository.save(result);
        deliveryWorkflow.markReadyForManagerReview(result.getMission().getOrder().getId());
        return toResponse(saved);
    }

    @Override
    @Transactional
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public MissionResultResponse reject(String resultId, MissionResultReviewRequest request) {
        MissionResult result = requirePendingResult(resultId);
        Instant now = Instant.now();
        result.setApprovalStatus(MissionResultApprovalStatus.REJECTED);
        result.setRejectedAt(now);
        result.setApprovedAt(null);
        result.setReviewedBy(currentUser.getCurrentUserId());
        result.setReviewNote(request == null ? null : request.getNote());
        return toResponse(missionResultRepository.save(result));
    }

    @Override
    @Transactional
    public void ensureCompletedResult(Mission mission) {
        missionRepository.findByIdForUpdate(mission.getId())
                .orElseThrow(() -> new ApiException(ErrorCode.MISSION_NOT_FOUND));
        if (missionResultRepository.existsByMissionId(mission.getId())) {
            return;
        }
        MissionResult result = new MissionResult();
        result.setMission(mission);
        result.setStatus(MissionResultStatus.COMPLETED);
        // Operational completion is not business submission. Never gate resource release here.
        result.setApprovalStatus(MissionResultApprovalStatus.DRAFT);
        result.setStartedAt(mission.getActualStartAt());
        result.setEndedAt(mission.getActualEndAt() != null ? mission.getActualEndAt() : mission.getCompletedAt());
        result.setCompletedAt(mission.getCompletedAt() != null ? mission.getCompletedAt() : Instant.now());
        result.setSubmittedAt(null);
        result.setMediaCount(countMedia(mission.getId()));
        attachMissionMedia(result, mission.getId());
        result.setSummary("Mission completed successfully");
        result.setDurationSeconds(resolveDurationSeconds(result.getStartedAt(), result.getEndedAt()));
        missionResultRepository.save(result);
    }

    private void applyRequest(MissionResult result, Mission mission, MissionResultRequest request) {
        Instant submittedAt = Instant.now();
        result.setStatus(request.getStatus() == null ? MissionResultStatus.COMPLETED : request.getStatus());
        result.setApprovalStatus(MissionResultApprovalStatus.PENDING_MANAGER_APPROVAL);
        result.setStartedAt(request.getStartedAt() != null ? request.getStartedAt() : mission.getActualStartAt());
        result.setEndedAt(request.getEndedAt() != null ? request.getEndedAt() : mission.getActualEndAt());
        result.setCompletedAt(request.getCompletedAt() != null ? request.getCompletedAt() : submittedAt);
        result.setSubmittedAt(submittedAt);
        result.setApprovedAt(null);
        result.setRejectedAt(null);
        result.setSummary(request.getSummary());
        result.setNotes(request.getNotes());
        result.setReviewedBy(null);
        result.setReviewNote(null);
        result.setMediaCount(countMedia(mission.getId()));
        attachMissionMedia(result, mission.getId());
        result.setDurationSeconds(resolveDurationSeconds(result.getStartedAt(), result.getEndedAt()));
    }

    private MissionResultResponse toResponse(MissionResult result) {
        var viewer = currentUser.getCurrentUser();
        if (viewer != null && viewer.getRole() != null && viewer.getRole().getCode() == RoleCode.CUSTOMER) {
            return missionResultMapper.toResponseWithoutMedia(result);
        }
        String missionId = result.getMission().getId();
        if (result.getMediaFiles() == null || result.getMediaFiles().isEmpty()) {
            result.setMediaFiles(mediaAssetRepository.findByMissionIdOrderByCapturedAtDesc(missionId));
        }
        return missionResultMapper.toResponse(result, mediaAssetService);
    }

    private void attachMissionMedia(MissionResult result, String missionId) {
        List<MediaAsset> missionMedia = mediaAssetRepository.findByMissionIdOrderByCapturedAtDesc(missionId);
        result.setMediaFiles(new ArrayList<>(missionMedia));
        result.setMediaCount(missionMedia.size());
    }

    private int countMedia(String missionId) {
        List<MediaAsset> media = mediaAssetRepository.findByMissionIdOrderByCapturedAtDesc(missionId);
        return media.size();
    }

    private MissionResult requirePendingResult(String resultId) {
        String missionId = missionResultRepository.findMissionIdByResultId(resultId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));
        missionRepository.findByIdForUpdate(missionId)
                .orElseThrow(() -> new ApiException(ErrorCode.MISSION_NOT_FOUND));
        return missionResultRepository.findById(resultId)
                .filter(result -> result.getApprovalStatus() == MissionResultApprovalStatus.PENDING_MANAGER_APPROVAL)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND,
                        "Pending mission result not found: " + resultId));
    }

    private Long resolveDurationSeconds(Instant startedAt, Instant endedAt) {
        if (startedAt == null || endedAt == null || endedAt.isBefore(startedAt)) {
            return null;
        }
        return Duration.between(startedAt, endedAt).getSeconds();
    }
}
