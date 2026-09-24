package com.ondemandmonitoring.media.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.ondemandmonitoring.common.exception.ApiException;
import com.ondemandmonitoring.drone.domain.Drone;
import com.ondemandmonitoring.drone.repository.DroneRepository;
import com.ondemandmonitoring.media.domain.*;
import com.ondemandmonitoring.media.dto.request.PrepareMediaUploadRequest;
import com.ondemandmonitoring.media.repository.*;
import com.ondemandmonitoring.media.service.impl.MediaUploadServiceImpl;
import com.ondemandmonitoring.mission.domain.*;
import com.ondemandmonitoring.mission.enums.MissionStatus;
import com.ondemandmonitoring.mission.repository.*;
import com.ondemandmonitoring.s3.*;
import com.ondemandmonitoring.user.domain.User;
import com.ondemandmonitoring.user.service.AuthenticatedUserResolver;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

class MediaUploadWorkflowTest {
    private final MissionRepository missions = mock(MissionRepository.class);
    private final MissionDroneAssignmentRepository droneAssignments = mock(MissionDroneAssignmentRepository.class);
    private final MissionOperatorAssignmentRepository operatorAssignments = mock(MissionOperatorAssignmentRepository.class);
    private final DroneRepository drones = mock(DroneRepository.class);
    private final MediaAssetRepository media = mock(MediaAssetRepository.class);
    private final MediaUploadAttemptRepository attempts = mock(MediaUploadAttemptRepository.class);
    private final ManualUploadTaskRepository manualTasks = mock(ManualUploadTaskRepository.class);
    private final MediaAuditLogRepository audit = mock(MediaAuditLogRepository.class);
    private final S3ObjectStorageService storage = mock(S3ObjectStorageService.class);
    private final AuthenticatedUserResolver userResolver = mock(AuthenticatedUserResolver.class);
    private final AwsS3Properties s3 = new AwsS3Properties();
    private final MediaUploadServiceImpl service = new MediaUploadServiceImpl(missions, droneAssignments,
            operatorAssignments, drones, media, attempts, manualTasks, audit, storage, s3, userResolver);

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "maxImageBytes", 25_000_000L);
        ReflectionTestUtils.setField(service, "maxVideoBytes", 1_000_000_000L);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("operator", "n/a", java.util.List.of()));
        User user = new User();
        user.setId(UUID.randomUUID().toString());
        when(userResolver.getCurrentUser()).thenReturn(user);
        Mission mission = new Mission();
        mission.setId("mission-id");
        mission.setStatus(MissionStatus.IN_FLIGHT);
        when(missions.findById("mission-id")).thenReturn(Optional.of(mission));
        MissionOperatorAssignment operator = new MissionOperatorAssignment();
        operator.setOperatorId(user.getId().toString());
        when(operatorAssignments.findByMissionIdAndIsCurrentTrue("mission-id"))
                .thenReturn(Optional.of(operator));
        Drone drone = new Drone();
        drone.setId("drone-id");
        drone.setDroneCode("DRONE-01");
        when(drones.findByDroneCode("DRONE-01")).thenReturn(Optional.of(drone));
        MissionDroneAssignment assignment = new MissionDroneAssignment();
        assignment.setDrone(drone);
        when(droneAssignments.findByMissionIdAndIsCurrentTrue("mission-id"))
                .thenReturn(Optional.of(assignment));
        when(storage.bucket()).thenReturn("test-bucket");
    }

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void rejectsMismatchedMediaBeforeIssuingS3Url() {
        var request = request("IMAGE", "video/mp4");
        assertThatThrownBy(() -> service.prepare("mission-id", request))
                .isInstanceOf(ApiException.class).hasMessageContaining("disagree");
        verify(storage, never()).createPresignedPutUrl(any(), any(), anyLong(), any());
    }

    @Test
    void repeatPrepareUsesSameAttempt() {
        var request = request("IMAGE", "image/jpeg");
        MediaAsset existing = new MediaAsset();
        existing.setId("media-id");
        existing.setMissionId("mission-id");
        existing.setType("IMAGE");
        existing.setContentType("image/jpeg");
        existing.setFileSize(100L);
        existing.setChecksumSha256(request.getChecksumSha256());
        existing.setMediaStatus(MediaStatus.UPLOAD_PENDING);
        when(media.findByMissionIdAndDroneCodeAndLocalMediaId("mission-id", "DRONE-01", "capture-1"))
                .thenReturn(Optional.of(existing));
        MediaUploadAttempt attempt = new MediaUploadAttempt();
        attempt.setId("attempt-id");
        attempt.setAttemptNumber(1);
        attempt.setStorageKey("staging/media-id/1.jpg");
        when(attempts.findFirstByMediaIdOrderByAttemptNumberDesc("media-id"))
                .thenReturn(Optional.of(attempt));
        when(storage.createPresignedPutUrl(any(), any(), anyLong(), any()))
                .thenReturn(new S3ObjectStorageService.PresignedUpload("https://s3.example/upload", Map.of(), 900));

        var result = service.prepare("mission-id", request);

        assertThat(result.getMediaId()).isEqualTo("media-id");
        assertThat(result.getAttemptId()).isEqualTo("attempt-id");
        verify(media, never()).saveAndFlush(any());
    }

    @Test
    void completedMissionAllowsItsReleasedOperatorAndDrone() {
        Mission mission = missions.findById("mission-id").orElseThrow();
        mission.setStatus(MissionStatus.COMPLETED);
        when(operatorAssignments.findByMissionIdAndIsCurrentTrue("mission-id"))
                .thenReturn(Optional.empty());
        when(droneAssignments.findByMissionIdAndIsCurrentTrue("mission-id"))
                .thenReturn(Optional.empty());
        MissionOperatorAssignment operator = new MissionOperatorAssignment();
        operator.setOperatorId(userResolver.getCurrentUser().getId().toString());
        operator.setStatus("COMPLETED");
        when(operatorAssignments.findByMissionId("mission-id")).thenReturn(List.of(operator));
        MissionDroneAssignment assignment = new MissionDroneAssignment();
        assignment.setDrone(drones.findByDroneCode("DRONE-01").orElseThrow());
        assignment.setReleaseReason("MISSION_COMPLETE");
        when(droneAssignments.findByMissionId("mission-id")).thenReturn(List.of(assignment));
        MediaAsset existing = new MediaAsset();
        existing.setId("media-id");
        existing.setMissionId("mission-id");
        existing.setType("IMAGE");
        existing.setContentType("image/jpeg");
        existing.setFileSize(100L);
        existing.setChecksumSha256("a".repeat(64));
        existing.setMediaStatus(MediaStatus.AVAILABLE);
        when(media.findByMissionIdAndDroneCodeAndLocalMediaId("mission-id", "DRONE-01", "capture-1"))
                .thenReturn(Optional.of(existing));
        when(media.findById("media-id")).thenReturn(Optional.of(existing));

        assertThat(service.prepare("mission-id", request("IMAGE", "image/jpeg")).getStatus())
                .isEqualTo(MediaStatus.AVAILABLE);
    }

    private PrepareMediaUploadRequest request(String type, String contentType) {
        return new PrepareMediaUploadRequest("DRONE-01", "capture-1", type, "capture.jpg",
                contentType, 100L, "a".repeat(64), Instant.now());
    }
}
