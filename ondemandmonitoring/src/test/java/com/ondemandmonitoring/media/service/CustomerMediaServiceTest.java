package com.ondemandmonitoring.media.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.ondemandmonitoring.common.exception.ApiException;
import com.ondemandmonitoring.media.domain.MediaAsset;
import com.ondemandmonitoring.media.domain.MediaStatus;
import com.ondemandmonitoring.media.repository.MediaAssetRepository;
import com.ondemandmonitoring.media.repository.MediaNotificationOutboxRepository;
import com.ondemandmonitoring.media.service.impl.CustomerMediaServiceImpl;
import com.ondemandmonitoring.mission.domain.Mission;
import com.ondemandmonitoring.mission.repository.MissionRepository;
import com.ondemandmonitoring.mission.repository.MissionDroneAssignmentRepository;
import com.ondemandmonitoring.mission.repository.MissionOperatorAssignmentRepository;
import com.ondemandmonitoring.mission.service.impl.MissionMediaAccessServiceImpl;
import com.ondemandmonitoring.order.domain.Order;
import com.ondemandmonitoring.s3.S3ObjectStorageService;
import com.ondemandmonitoring.user.domain.User;
import com.ondemandmonitoring.user.service.AuthenticatedUserResolver;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CustomerMediaServiceTest {
    private final MissionRepository missions = mock(MissionRepository.class);
    private final MediaAssetRepository media = mock(MediaAssetRepository.class);
    private final MediaNotificationOutboxRepository notifications = mock(MediaNotificationOutboxRepository.class);
    private final S3ObjectStorageService storage = mock(S3ObjectStorageService.class);
    private final AuthenticatedUserResolver currentUser = mock(AuthenticatedUserResolver.class);
    private final CustomerMediaServiceImpl service = new CustomerMediaServiceImpl(
            new MissionMediaAccessServiceImpl(missions, mock(MissionDroneAssignmentRepository.class),
                    mock(MissionOperatorAssignmentRepository.class), currentUser), media, notifications, storage,
            org.mapstruct.factory.Mappers.getMapper(com.ondemandmonitoring.media.mapper.MediaWorkflowMapper.class));

    @Test
    void onlyAvailableMediaGetsDownloadUrl() {
        User customer = new User();
        customer.setId(UUID.randomUUID().toString());
        when(currentUser.getCurrentUserId()).thenReturn(customer.getId());
        Order order = new Order();
        order.setCustomer(customer);
        Mission mission = new Mission();
        mission.setId("mission-1");
        mission.setOrder(order);
        when(missions.findById("mission-1")).thenReturn(Optional.of(mission));

        MediaAsset staged = new MediaAsset();
        staged.setMediaStatus(MediaStatus.VALIDATING);
        MediaAsset ready = new MediaAsset();
        ready.setId("ready-1");
        ready.setMediaStatus(MediaStatus.AVAILABLE);
        ready.setFileSize(10L);
        ready.setS3Bucket("bucket");
        ready.setS3Key("final/ready-1.jpg");
        when(media.findByMissionIdOrderByCapturedAtDesc("mission-1"))
                .thenReturn(List.of(staged, ready));
        when(storage.createPresignedGetUrl("bucket", "final/ready-1.jpg"))
                .thenReturn("https://s3.example/download");

        var result = service.listAvailable("mission-1");

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getMediaId()).isEqualTo("ready-1");
        verify(storage, times(1)).createPresignedGetUrl("bucket", "final/ready-1.jpg");
    }

    @Test
    void rejectsCustomerFromAnotherOrder() {
        User owner = new User();
        owner.setId(UUID.randomUUID().toString());
        User other = new User();
        other.setId(UUID.randomUUID().toString());
        when(currentUser.getCurrentUserId()).thenReturn(other.getId());
        Order order = new Order();
        order.setCustomer(owner);
        Mission mission = new Mission();
        mission.setOrder(order);
        when(missions.findById("mission-1")).thenReturn(Optional.of(mission));

        assertThatThrownBy(() -> service.listAvailable("mission-1"))
                .isInstanceOf(ApiException.class);
        verifyNoInteractions(storage);
    }
}
