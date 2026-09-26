package com.ondemandmonitoring.media.service.impl;

import com.ondemandmonitoring.common.exception.ApiException;
import com.ondemandmonitoring.common.exception.ErrorCode;
import com.ondemandmonitoring.media.domain.MediaAsset;
import com.ondemandmonitoring.media.domain.MediaStatus;
import com.ondemandmonitoring.media.dto.response.CustomerMediaNotificationResponse;
import com.ondemandmonitoring.media.dto.response.CustomerMediaResponse;
import com.ondemandmonitoring.media.repository.MediaAssetRepository;
import com.ondemandmonitoring.media.repository.MediaNotificationOutboxRepository;
import com.ondemandmonitoring.media.service.ICustomerMediaService;
import com.ondemandmonitoring.mission.service.IMissionMediaAccessService;
import com.ondemandmonitoring.s3.S3ObjectStorageService;
import java.util.List;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class CustomerMediaServiceImpl implements ICustomerMediaService {

    IMissionMediaAccessService missionAccess;
    MediaAssetRepository media;
    MediaNotificationOutboxRepository notifications;
    S3ObjectStorageService storage;

    @Override
    @Transactional(readOnly = true)
    public List<CustomerMediaResponse> listAvailable(String missionId) {
        String canonicalMissionId = missionAccess.authorizeCustomer(missionId);
        return media.findByMissionIdOrderByCapturedAtDesc(canonicalMissionId).stream()
                .filter(asset -> asset.getMediaStatus() == MediaStatus.AVAILABLE)
                .map(this::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<CustomerMediaResponse> listAllAvailable() {
        List<String> missionIds = ownMissionIds();
        if (missionIds.isEmpty()) return List.of();
        return media.findByMissionIdInAndMediaStatusOrderByCapturedAtDesc(missionIds,
                        MediaStatus.AVAILABLE)
                .stream().map(this::toResponse).toList();
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
        return toResponse(asset);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CustomerMediaNotificationResponse> listNotifications(String missionId) {
        String canonicalMissionId = missionAccess.authorizeCustomer(missionId);
        return notifications.findByMedia_MissionIdOrderByCreatedAtDesc(canonicalMissionId).stream()
                .filter(event -> event.getMedia().getMediaStatus() == MediaStatus.AVAILABLE)
                .map(event -> new CustomerMediaNotificationResponse(event.getId(), event.getMedia().getId(),
                        canonicalMissionId,
                        event.getEventType(),
                        event.getCreatedAt()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<CustomerMediaNotificationResponse> listAllNotifications() {
        List<String> missionIds = ownMissionIds();
        if (missionIds.isEmpty()) return List.of();
        return notifications.findByMedia_MissionIdInOrderByCreatedAtDesc(missionIds).stream()
                .filter(event -> event.getMedia().getMediaStatus() == MediaStatus.AVAILABLE)
                .map(event -> new CustomerMediaNotificationResponse(
                        event.getId(),
                        event.getMedia().getId(),
                        event.getMedia().getMissionId(),
                        event.getEventType(),
                        event.getCreatedAt()))
                .toList();
    }

    private List<String> ownMissionIds() {
        return missionAccess.ownCustomerMissionIds();
    }

    private CustomerMediaResponse toResponse(MediaAsset asset) {
        return new CustomerMediaResponse(
                asset.getId(),
                asset.getMissionId(),
                asset.getDroneCode(),
                asset.getType(),
                asset.getOriginalFileName(),
                asset.getContentType(),
                asset.getFileSize(),
                asset.getCapturedAt(),
                asset.getAvailableAt(),
                storage.createPresignedGetUrl(asset.getS3Bucket(), asset.getS3Key()));
    }
}
