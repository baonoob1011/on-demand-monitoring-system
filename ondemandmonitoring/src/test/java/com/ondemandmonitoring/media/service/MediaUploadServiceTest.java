package com.ondemandmonitoring.media.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ondemandmonitoring.device.domain.Device;
import com.ondemandmonitoring.device.domain.DeviceImage;
import com.ondemandmonitoring.device.repository.DeviceImageRepository;
import com.ondemandmonitoring.device.repository.DeviceRepository;
import com.ondemandmonitoring.media.domain.MediaStatus;
import com.ondemandmonitoring.media.domain.MediaUploadAttempt;
import com.ondemandmonitoring.media.dto.PrepareMediaUploadRequest;
import com.ondemandmonitoring.media.repository.ManualUploadTaskRepository;
import com.ondemandmonitoring.media.repository.MediaUploadAttemptRepository;
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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

class MediaUploadServiceTest {

    private MissionRepository missionRepository;
    private DeviceRepository deviceRepository;
    private DeviceImageRepository mediaRepository;
    private MediaUploadAttemptRepository attemptRepository;
    private S3ObjectStorageService storage;
    private IUserService userService;
    private MediaUploadService service;

    @BeforeEach
    void setUp() {
        missionRepository = mock(MissionRepository.class);
        deviceRepository = mock(DeviceRepository.class);
        mediaRepository = mock(DeviceImageRepository.class);
        attemptRepository = mock(MediaUploadAttemptRepository.class);
        storage = mock(S3ObjectStorageService.class);
        userService = mock(IUserService.class);
        AwsS3Properties properties = new AwsS3Properties();
        properties.setPrefix("monitoring");
        service = new MediaUploadService(
                missionRepository,
                deviceRepository,
                mediaRepository,
                attemptRepository,
                mock(ManualUploadTaskRepository.class),
                storage,
                properties,
                mock(ApplicationEventPublisher.class),
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
        when(mediaRepository.save(any(DeviceImage.class))).thenAnswer(invocation -> {
            DeviceImage media = invocation.getArgument(0);
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
}
