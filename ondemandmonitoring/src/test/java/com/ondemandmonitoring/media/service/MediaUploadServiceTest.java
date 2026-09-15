package com.ondemandmonitoring.media.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ondemandmonitoring.device.domain.Device;
import com.ondemandmonitoring.device.repository.DeviceRepository;
import com.ondemandmonitoring.media.domain.MediaStatus;
import com.ondemandmonitoring.media.domain.MediaAsset;
import com.ondemandmonitoring.media.domain.MediaNotificationOutbox;
import com.ondemandmonitoring.media.domain.MediaUploadAttempt;
import com.ondemandmonitoring.media.domain.StorageEventInbox;
import com.ondemandmonitoring.media.domain.UploadAttemptStatus;
import com.ondemandmonitoring.common.exception.ApiException;
import com.ondemandmonitoring.media.dto.PrepareMediaUploadRequest;
import com.ondemandmonitoring.media.event.StorageObjectCreatedEvent;
import com.ondemandmonitoring.media.service.impl.MediaUploadServiceImpl;
import com.ondemandmonitoring.media.repository.ManualUploadTaskRepository;
import com.ondemandmonitoring.media.repository.MediaAssetRepository;
import com.ondemandmonitoring.media.repository.MediaNotificationOutboxRepository;
import com.ondemandmonitoring.media.repository.MediaUploadAttemptRepository;
import com.ondemandmonitoring.media.repository.StorageEventInboxRepository;
import com.ondemandmonitoring.mission.domain.Mission;
import com.ondemandmonitoring.mission.enums.MissionStatus;
import com.ondemandmonitoring.mission.repository.MissionRepository;
import com.ondemandmonitoring.s3.AwsS3Properties;
import com.ondemandmonitoring.s3.S3ObjectStorageService;
import com.ondemandmonitoring.user.domain.User;
import com.ondemandmonitoring.user.service.IUserService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

class MediaUploadServiceTest {

    private MissionRepository missionRepository;
    private DeviceRepository deviceRepository;
    private MediaAssetRepository mediaRepository;
    private MediaUploadAttemptRepository attemptRepository;
    private MediaNotificationOutboxRepository notificationOutboxRepository;
    private StorageEventInboxRepository storageEventInboxRepository;
    private S3ObjectStorageService storage;
    private IUserService userService;
    private MediaUploadServiceImpl service;

    @BeforeEach
    void setUp() {
        missionRepository = mock(MissionRepository.class);
        deviceRepository = mock(DeviceRepository.class);
        mediaRepository = mock(MediaAssetRepository.class);
        attemptRepository = mock(MediaUploadAttemptRepository.class);
        notificationOutboxRepository = mock(MediaNotificationOutboxRepository.class);
        storageEventInboxRepository = mock(StorageEventInboxRepository.class);
        storage = mock(S3ObjectStorageService.class);
        userService = mock(IUserService.class);
        AwsS3Properties properties = new AwsS3Properties();
        properties.setPrefix("monitoring");
        service = new MediaUploadServiceImpl(
                missionRepository,
                deviceRepository,
                mediaRepository,
                attemptRepository,
                mock(ManualUploadTaskRepository.class),
                notificationOutboxRepository,
                storageEventInboxRepository,
                storage,
                properties,
                userService);
        ReflectionTestUtils.setField(service, "maxImageBytes", 1_000_000L);
        ReflectionTestUtils.setField(service, "maxVideoBytes", 10_000_000L);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        "cognito-sub-1", "n/a", List.of(new SimpleGrantedAuthority("ROLE_DRONE_OPERATOR"))));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void prepareCreatesPendingMediaAndPresignedAttempt() {
        Device drone = new Device();
        drone.setId("device-1");
        drone.setDeviceCode("DRONE-01");
        Mission mission = new Mission();
        mission.setId("mission-1");
        mission.setStatus(MissionStatus.IN_FLIGHT);
        UUID operatorId = UUID.fromString("00000000-0000-0000-0000-000000000003");
        mission.setOperatorId(operatorId.toString());
        mission.setDevice(drone);
        User operator = User.builder().id(operatorId).build();
        when(userService.findByCognitoSub("cognito-sub-1")).thenReturn(operator);
        when(missionRepository.findById("mission-1")).thenReturn(Optional.of(mission));
        when(deviceRepository.findByDeviceCode("DRONE-01")).thenReturn(Optional.of(drone));
        when(mediaRepository.findByMissionIdAndDeviceIdAndLocalMediaId(any(), any(), any()))
                .thenReturn(Optional.empty());
        when(storage.bucket()).thenReturn("media-bucket");
        when(storage.createPresignedPutUrl(any(), any(), any(Long.class), any()))
                .thenReturn(new S3ObjectStorageService.PresignedUpload(
                        "https://upload.example", Map.of("content-type", List.of("image/jpeg")), 900));
        when(mediaRepository.save(any(MediaAsset.class))).thenAnswer(invocation -> {
            MediaAsset media = invocation.getArgument(0);
            if (media.getId() == null) media.setId("media-1");
            return media;
        });
        when(attemptRepository.save(any(MediaUploadAttempt.class))).thenAnswer(invocation -> {
            MediaUploadAttempt attempt = invocation.getArgument(0);
            attempt.setId("attempt-1");
            return attempt;
        });

        var response = service.prepare("mission-1", new PrepareMediaUploadRequest(
                "DRONE-01", "local-1", "IMAGE", "capture.jpg", "image/jpeg", 12L,
                "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
                Instant.parse("2026-09-12T10:00:00Z")));

        assertThat(response.mediaId()).isEqualTo("media-1");
        assertThat(response.attemptId()).isEqualTo("attempt-1");
        assertThat(response.status()).isEqualTo(MediaStatus.UPLOAD_PENDING);
        assertThat(response.uploadUrl()).isEqualTo("https://upload.example");
        verify(attemptRepository).save(any(MediaUploadAttempt.class));
    }

    @Test
    void objectCreatedMakesMediaAvailableAndPersistsOutboxAndInbox() {
        byte[] jpeg = {(byte) 0xff, (byte) 0xd8, (byte) 0xff, 1, 2, 3};
        MediaAsset media = new MediaAsset();
        media.setId("media-1");
        media.setMissionId("mission-1");
        media.setMediaStatus(MediaStatus.VALIDATING);
        media.setS3Bucket("media-bucket");
        media.setS3Key("drone-media/image.jpg");
        media.setFileSize((long) jpeg.length);
        media.setContentType("image/jpeg");
        media.setChecksumSha256(sha256(jpeg));
        media.setUploadAttemptCount(1);
        MediaUploadAttempt attempt = new MediaUploadAttempt();
        attempt.setId("attempt-1");
        attempt.setStatus(UploadAttemptStatus.PENDING);

        when(storageEventInboxRepository.existsByEventKey(any())).thenReturn(false);
        when(mediaRepository.findByS3BucketAndS3Key("media-bucket", "drone-media/image.jpg"))
                .thenReturn(Optional.of(media));
        when(storage.open("media-bucket", "drone-media/image.jpg"))
                .thenReturn(new S3ObjectStorageService.StoredObjectStream(
                        new java.io.ByteArrayInputStream(jpeg), (long) jpeg.length, "image/jpeg"));
        when(attemptRepository.findTopByMediaIdOrderByAttemptNumberDesc("media-1"))
                .thenReturn(Optional.of(attempt));

        service.processObjectCreated(new StorageObjectCreatedEvent(
                "media-bucket", "drone-media/image.jpg", (long) jpeg.length,
                null, null, "sequencer-1", "ObjectCreated:Put", Instant.now()));

        assertThat(media.getMediaStatus()).isEqualTo(MediaStatus.AVAILABLE);
        assertThat(attempt.getStatus()).isEqualTo(UploadAttemptStatus.SUCCEEDED);
        verify(notificationOutboxRepository).save(any(MediaNotificationOutbox.class));
        verify(storageEventInboxRepository).save(any(StorageEventInbox.class));

        Mission mission = new Mission();
        mission.setId("mission-1");
        when(missionRepository.findById("mission-1")).thenReturn(Optional.of(mission));
        when(mediaRepository.findById("media-1")).thenReturn(Optional.of(media));
        when(attemptRepository.findByIdAndMediaId("attempt-1", "media-1"))
                .thenReturn(Optional.of(attempt));
        authenticateAdmin();
        clearInvocations(mediaRepository, attemptRepository);

        service.markUploaded("media-1", "attempt-1");

        assertThat(media.getMediaStatus()).isEqualTo(MediaStatus.AVAILABLE);
        assertThat(attempt.getStatus()).isEqualTo(UploadAttemptStatus.SUCCEEDED);
        verify(mediaRepository, never()).save(any(MediaAsset.class));
        verify(attemptRepository, never()).save(any(MediaUploadAttempt.class));
    }

    @Test
    void markUploadedBeforeObjectCreatedAlsoEndsInAvailable() {
        byte[] jpeg = {(byte) 0xff, (byte) 0xd8, (byte) 0xff, 1, 2, 3};
        MediaAsset media = new MediaAsset();
        media.setId("media-1");
        media.setMissionId("mission-1");
        media.setMediaStatus(MediaStatus.UPLOAD_PENDING);
        media.setS3Bucket("media-bucket");
        media.setS3Key("drone-media/image.jpg");
        media.setFileSize((long) jpeg.length);
        media.setContentType("image/jpeg");
        media.setChecksumSha256(sha256(jpeg));
        media.setUploadAttemptCount(1);
        MediaUploadAttempt attempt = new MediaUploadAttempt();
        attempt.setId("attempt-1");
        attempt.setStatus(UploadAttemptStatus.PENDING);
        arrangeMarkUploaded(media, attempt);
        when(storageEventInboxRepository.existsByEventKey(any())).thenReturn(false);
        when(mediaRepository.findByS3BucketAndS3Key("media-bucket", "drone-media/image.jpg"))
                .thenReturn(Optional.of(media));
        when(storage.open("media-bucket", "drone-media/image.jpg"))
                .thenReturn(new S3ObjectStorageService.StoredObjectStream(
                        new java.io.ByteArrayInputStream(jpeg), (long) jpeg.length, "image/jpeg"));
        when(attemptRepository.findTopByMediaIdOrderByAttemptNumberDesc("media-1"))
                .thenReturn(Optional.of(attempt));

        service.markUploaded("media-1", "attempt-1");
        service.processObjectCreated(new StorageObjectCreatedEvent(
                "media-bucket", "drone-media/image.jpg", (long) jpeg.length,
                null, null, "sequencer-2", "ObjectCreated:Put", Instant.now()));

        assertThat(media.getMediaStatus()).isEqualTo(MediaStatus.AVAILABLE);
        assertThat(attempt.getStatus()).isEqualTo(UploadAttemptStatus.SUCCEEDED);
    }

    @Test
    void markUploadedIsIdempotentAfterUploadedAcknowledgement() {
        MediaAsset media = new MediaAsset();
        media.setId("media-1");
        media.setMissionId("mission-1");
        media.setMediaStatus(MediaStatus.VALIDATING);
        MediaUploadAttempt attempt = new MediaUploadAttempt();
        attempt.setId("attempt-1");
        attempt.setStatus(UploadAttemptStatus.UPLOADED);
        arrangeMarkUploaded(media, attempt);

        service.markUploaded("media-1", "attempt-1");

        assertThat(media.getMediaStatus()).isEqualTo(MediaStatus.VALIDATING);
        assertThat(attempt.getStatus()).isEqualTo(UploadAttemptStatus.UPLOADED);
        verify(mediaRepository, never()).save(any(MediaAsset.class));
        verify(attemptRepository, never()).save(any(MediaUploadAttempt.class));
    }

    @Test
    void markUploadedDoesNotRegressAvailableMediaWithPendingAttempt() {
        MediaAsset media = new MediaAsset();
        media.setId("media-1");
        media.setMissionId("mission-1");
        media.setMediaStatus(MediaStatus.AVAILABLE);
        MediaUploadAttempt attempt = new MediaUploadAttempt();
        attempt.setId("attempt-1");
        attempt.setStatus(UploadAttemptStatus.PENDING);
        arrangeMarkUploaded(media, attempt);

        service.markUploaded("media-1", "attempt-1");

        assertThat(media.getMediaStatus()).isEqualTo(MediaStatus.AVAILABLE);
        assertThat(attempt.getStatus()).isEqualTo(UploadAttemptStatus.PENDING);
        verify(mediaRepository, never()).save(any(MediaAsset.class));
        verify(attemptRepository, never()).save(any(MediaUploadAttempt.class));
    }

    @Test
    void markUploadedDoesNotRegressSucceededAttemptWhileMediaIsValidating() {
        MediaAsset media = new MediaAsset();
        media.setId("media-1");
        media.setMissionId("mission-1");
        media.setMediaStatus(MediaStatus.VALIDATING);
        MediaUploadAttempt attempt = new MediaUploadAttempt();
        attempt.setId("attempt-1");
        attempt.setStatus(UploadAttemptStatus.SUCCEEDED);
        arrangeMarkUploaded(media, attempt);

        service.markUploaded("media-1", "attempt-1");

        assertThat(media.getMediaStatus()).isEqualTo(MediaStatus.VALIDATING);
        assertThat(attempt.getStatus()).isEqualTo(UploadAttemptStatus.SUCCEEDED);
        verify(mediaRepository, never()).save(any(MediaAsset.class));
        verify(attemptRepository, never()).save(any(MediaUploadAttempt.class));
    }

    @Test
    void markUploadedTransitionsPendingAttemptOnlyOnce() {
        MediaAsset media = new MediaAsset();
        media.setId("media-1");
        media.setMissionId("mission-1");
        media.setMediaStatus(MediaStatus.UPLOAD_PENDING);
        MediaUploadAttempt attempt = new MediaUploadAttempt();
        attempt.setId("attempt-1");
        attempt.setStatus(UploadAttemptStatus.PENDING);
        arrangeMarkUploaded(media, attempt);

        service.markUploaded("media-1", "attempt-1");
        service.markUploaded("media-1", "attempt-1");

        assertThat(media.getMediaStatus()).isEqualTo(MediaStatus.VALIDATING);
        assertThat(attempt.getStatus()).isEqualTo(UploadAttemptStatus.UPLOADED);
        verify(mediaRepository).save(media);
        verify(attemptRepository).save(attempt);
    }

    @Test
    void markUploadedRejectsFailedAttemptWhenMediaIsNotAvailable() {
        MediaAsset media = new MediaAsset();
        media.setId("media-1");
        media.setMissionId("mission-1");
        media.setMediaStatus(MediaStatus.RETRY_REQUIRED);
        MediaUploadAttempt attempt = new MediaUploadAttempt();
        attempt.setId("attempt-1");
        attempt.setStatus(UploadAttemptStatus.FAILED);
        arrangeMarkUploaded(media, attempt);

        assertThatThrownBy(() -> service.markUploaded("media-1", "attempt-1"))
                .isInstanceOf(ApiException.class);
        verify(mediaRepository, never()).save(any(MediaAsset.class));
        verify(attemptRepository, never()).save(any(MediaUploadAttempt.class));
    }

    private void arrangeMarkUploaded(MediaAsset media, MediaUploadAttempt attempt) {
        Mission mission = new Mission();
        mission.setId(media.getMissionId());
        when(missionRepository.findById(media.getMissionId())).thenReturn(Optional.of(mission));
        when(mediaRepository.findById(media.getId())).thenReturn(Optional.of(media));
        when(attemptRepository.findByIdAndMediaId(attempt.getId(), media.getId()))
                .thenReturn(Optional.of(attempt));
        authenticateAdmin();
    }

    private void authenticateAdmin() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        "cognito-admin", "n/a", List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
    }

    private String sha256(byte[] bytes) {
        try {
            return java.util.HexFormat.of().formatHex(
                    java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new AssertionError(exception);
        }
    }
}
