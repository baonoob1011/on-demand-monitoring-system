package com.ondemandmonitoring.media.service.impl;

import com.ondemandmonitoring.common.exception.ApiException;
import com.ondemandmonitoring.common.exception.ErrorCode;
import com.ondemandmonitoring.delivery.service.IDeliveryWorkflowService;
import com.ondemandmonitoring.media.domain.MediaAsset;
import com.ondemandmonitoring.media.enums.MediaStatus;
import com.ondemandmonitoring.media.dto.response.CustomerMediaNotificationResponse;
import com.ondemandmonitoring.media.dto.response.CustomerMediaResponse;
import com.ondemandmonitoring.media.dto.response.CustomerMissionMediaStatusResponse;
import com.ondemandmonitoring.media.mapper.MediaWorkflowMapper;
import com.ondemandmonitoring.media.repository.MediaAssetRepository;
import com.ondemandmonitoring.media.repository.MediaNotificationOutboxRepository;
import com.ondemandmonitoring.media.service.ICustomerMediaService;
import com.ondemandmonitoring.mission.service.IMissionMediaAccessService;
import com.ondemandmonitoring.media.service.IMediaObjectStorage;
import com.ondemandmonitoring.media.service.IResultRetentionAccessService;
import java.util.List;
import com.ondemandmonitoring.common.api.PageResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor(onConstructor_ = @Autowired)
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class CustomerMediaServiceImpl implements ICustomerMediaService {

    IMissionMediaAccessService missionAccess;
    MediaAssetRepository media;
    MediaNotificationOutboxRepository notifications;
    IMediaObjectStorage storage;
    MediaWorkflowMapper mapper;
    IResultRetentionAccessService retentionAccess;
    @Autowired @NonFinal IDeliveryWorkflowService deliveryWorkflow;

    /** Compatibility constructor for focused unit tests and non-Spring callers. */
    public CustomerMediaServiceImpl(IMissionMediaAccessService missionAccess,
            MediaAssetRepository media, MediaNotificationOutboxRepository notifications,
            IMediaObjectStorage storage, MediaWorkflowMapper mapper) {
        this(missionAccess, media, notifications, storage, mapper,
                new ResultRetentionAccessServiceImpl(java.time.Clock.systemUTC()));
    }

    @Override
    @Transactional(readOnly = true)
    public CustomerMissionMediaStatusResponse getMissionMediaStatus(String missionId) {
        String canonicalId = missionAccess.authorizeCustomer(missionId);
        return CustomerMissionMediaStatusResponse.builder()
                .availableCount(media.countByMissionIdAndMediaStatus(canonicalId, MediaStatus.AVAILABLE))
                .processingCount(media.countByMissionIdAndMediaStatusIn(canonicalId, List.of(
                        MediaStatus.UPLOAD_PENDING, MediaStatus.UPLOADING, MediaStatus.VALIDATING,
                        MediaStatus.RETRY_REQUIRED, MediaStatus.MANUAL_UPLOAD_REQUIRED,
                        MediaStatus.PENDING_MANAGER_APPROVAL)))
                .rejectedCount(media.countByMissionIdAndMediaStatus(canonicalId, MediaStatus.REJECTED))
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<CustomerMediaResponse> listAvailablePage(String missionId, int page, int size) {
        String canonicalId = missionAccess.authorizeCustomer(missionId);
        boolean originalsAvailable = deliveredMissionIds(List.of(canonicalId)).contains(canonicalId);
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "capturedAt", "id"));
        return PageResponse.from(media.findByMissionIdAndMediaStatus(
                canonicalId, MediaStatus.AVAILABLE, pageable).map(asset -> toResponse(asset, originalsAvailable)));
    }

    @Override
    @Transactional(readOnly = true)
    public CustomerMediaResponse getAvailableInMission(String missionId, String mediaId) {
        String canonicalId = missionAccess.authorizeCustomer(missionId);
        boolean originalsAvailable = deliveredMissionIds(List.of(canonicalId)).contains(canonicalId);
        return media.findById(mediaId)
                .filter(asset -> canonicalId.equals(asset.getMissionId()))
                .filter(asset -> asset.getMediaStatus() == MediaStatus.AVAILABLE)
                .map(asset -> toResponse(asset, originalsAvailable))
                .orElseThrow(() -> new ApiException(ErrorCode.MEDIA_NOT_FOUND));
    }

    @Override
    @Transactional(readOnly = true)
    public List<CustomerMediaResponse> listAvailable(String missionId) {
        String canonicalId = missionAccess.authorizeCustomer(missionId);
        boolean originalsAvailable = deliveredMissionIds(List.of(canonicalId)).contains(canonicalId);
        return media.findByMissionIdOrderByCapturedAtDesc(canonicalId).stream()
                .filter(asset -> asset.getMediaStatus() == MediaStatus.AVAILABLE)
                .map(asset -> toResponse(asset, originalsAvailable)).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<CustomerMediaResponse> listAllAvailable() {
        List<String> missionIds = ownMissionIds();
        if (missionIds.isEmpty())
            return List.of();
        var delivered = new java.util.HashSet<>(deliveredMissionIds(missionIds));
        return media.findByMissionIdInAndMediaStatusOrderByCapturedAtDesc(missionIds,
                MediaStatus.AVAILABLE)
                .stream().map(asset -> toResponse(asset, delivered.contains(asset.getMissionId()))).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public CustomerMediaResponse getAvailable(String mediaId) {
        MediaAsset asset = media.findById(mediaId)
                .orElseThrow(() -> new ApiException(ErrorCode.MEDIA_NOT_FOUND));
        missionAccess.authorizeCustomer(asset.getMissionId());
        if (asset.getMediaStatus() != MediaStatus.AVAILABLE) {
            throw new ApiException(ErrorCode.MEDIA_NOT_FOUND);
        }
        return toResponse(asset, deliveredMissionIds(List.of(asset.getMissionId())).contains(asset.getMissionId()));
    }

    @Override
    @Transactional(readOnly = true)
    public List<CustomerMediaNotificationResponse> listNotifications(String missionId) {
        String canonicalId = missionAccess.authorizeCustomer(missionId);
        return notifications.findByMedia_MissionIdOrderByCreatedAtDesc(canonicalId).stream()
                .filter(event -> event.getMedia().getMediaStatus() == MediaStatus.AVAILABLE)
                .map(mapper::toNotificationResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<CustomerMediaNotificationResponse> listAllNotifications() {
        List<String> missionIds = ownMissionIds();
        if (missionIds.isEmpty())
            return List.of();
        return notifications.findByMedia_MissionIdInOrderByCreatedAtDesc(missionIds).stream()
                .filter(event -> event.getMedia().getMediaStatus() == MediaStatus.AVAILABLE)
                .map(mapper::toNotificationResponse)
                .toList();
    }

    private List<String> ownMissionIds() {
        return missionAccess.ownCustomerMissionIds();
    }

    private List<String> deliveredMissionIds(List<String> missionIds) {
        // Normal ineligibility must not be represented by an exception here. Catching an
        // exception from another transactional bean still marks the outer transaction as
        // rollback-only and causes UnexpectedRollbackException during commit.
        if (deliveryWorkflow == null) return missionIds;
        return deliveryWorkflow.originalAccessibleMissionIds(missionIds);
    }

    private CustomerMediaResponse toResponse(MediaAsset asset, boolean originalsAvailable) {
        if (originalsAvailable) retentionAccess.requireAccessible(asset);
        return mapper.toCustomerResponse(asset,
                originalsAvailable ? storage.createPresignedGetUrl(asset.getS3Bucket(), asset.getS3Key()) : null);
    }
}
