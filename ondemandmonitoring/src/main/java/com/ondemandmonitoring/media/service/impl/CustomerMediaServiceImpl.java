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
import com.ondemandmonitoring.mission.domain.Mission;
import com.ondemandmonitoring.mission.repository.MissionRepository;
import com.ondemandmonitoring.s3.S3ObjectStorageService;
import com.ondemandmonitoring.user.service.AuthenticatedUserResolver;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CustomerMediaServiceImpl implements ICustomerMediaService {
    private final MissionRepository missions;
    private final MediaAssetRepository media;
    private final MediaNotificationOutboxRepository notifications;
    private final S3ObjectStorageService storage;
    private final AuthenticatedUserResolver currentUser;

    @Override
    @Transactional(readOnly = true)
    public List<CustomerMediaResponse> listAvailable(String missionId) {
        Mission mission = authorize(missionId);
        return media.findByMissionIdOrderByCapturedAtDesc(mission.getId()).stream()
                .filter(asset -> asset.getMediaStatus() == MediaStatus.AVAILABLE)
                .map(this::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<CustomerMediaResponse> listAllAvailable() {
        List<String> missionIds = ownMissionIds();
        if (missionIds.isEmpty()) return List.of();
        return media.findByMissionIdInAndMediaStatusOrderByCapturedAtDesc(missionIds, MediaStatus.AVAILABLE)
                .stream().map(this::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public CustomerMediaResponse getAvailable(String mediaId) {
        MediaAsset asset = media.findById(mediaId)
                .orElseThrow(() -> new ApiException(ErrorCode.MEDIA_NOT_FOUND));
        authorize(asset.getMissionId());
        if (asset.getMediaStatus() != MediaStatus.AVAILABLE) {
            throw new ApiException(ErrorCode.MEDIA_NOT_FOUND);
        }
        return toResponse(asset);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CustomerMediaNotificationResponse> listNotifications(String missionId) {
        Mission mission = authorize(missionId);
        return notifications.findByMedia_MissionIdOrderByCreatedAtDesc(mission.getId()).stream()
                .filter(event -> event.getMedia().getMediaStatus() == MediaStatus.AVAILABLE)
                .map(event -> new CustomerMediaNotificationResponse(event.getId(), event.getMedia().getId(),
                        mission.getId(), event.getEventType(), event.getCreatedAt()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<CustomerMediaNotificationResponse> listAllNotifications() {
        List<String> missionIds = ownMissionIds();
        if (missionIds.isEmpty()) return List.of();
        return notifications.findByMedia_MissionIdInOrderByCreatedAtDesc(missionIds).stream()
                .filter(event -> event.getMedia().getMediaStatus() == MediaStatus.AVAILABLE)
                .map(event -> new CustomerMediaNotificationResponse(event.getId(), event.getMedia().getId(),
                        event.getMedia().getMissionId(), event.getEventType(), event.getCreatedAt()))
                .toList();
    }

    private List<String> ownMissionIds() {
        return missions.findByOrder_Customer_Id(currentUser.getCurrentUser().getId()).stream()
                .map(Mission::getId).toList();
    }

    private Mission authorize(String identifier) {
        Mission mission = missions.findById(identifier).or(() -> missions.findByMissionCode(identifier))
                .orElseThrow(() -> new ApiException(ErrorCode.MISSION_NOT_FOUND));
        if (mission.getOrder() == null || mission.getOrder().getCustomer() == null
                || !mission.getOrder().getCustomer().getId().equals(currentUser.getCurrentUser().getId())) {
            throw new ApiException(ErrorCode.ACCESS_DENIED);
        }
        return mission;
    }

    private CustomerMediaResponse toResponse(MediaAsset asset) {
        return new CustomerMediaResponse(asset.getId(), asset.getMissionId(), asset.getDroneCode(),
                asset.getType(), asset.getOriginalFileName(), asset.getContentType(), asset.getFileSize(),
                asset.getCapturedAt(), asset.getAvailableAt(),
                storage.createPresignedGetUrl(asset.getS3Bucket(), asset.getS3Key()));
    }
}
