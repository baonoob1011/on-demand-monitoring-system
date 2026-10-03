package com.ondemandmonitoring.media.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.ondemandmonitoring.common.exception.ApiException;
import com.ondemandmonitoring.device.domain.Device;
import com.ondemandmonitoring.device.service.IDeviceService;
import com.ondemandmonitoring.media.domain.*;
import com.ondemandmonitoring.media.dto.request.PrepareMediaUploadRequest;
import com.ondemandmonitoring.media.repository.*;
import com.ondemandmonitoring.media.service.impl.MediaUploadServiceImpl;
import com.ondemandmonitoring.mission.domain.*;
import com.ondemandmonitoring.mission.enums.MissionStatus;
import com.ondemandmonitoring.mission.repository.*;
import com.ondemandmonitoring.mission.service.impl.MissionMediaAccessServiceImpl;
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
    private final MissionDeviceAssignmentRepository deviceAssignments = mock(MissionDeviceAssignmentRepository.class);
    private final MissionStaffAssignmentRepository staffAssignments = mock(MissionStaffAssignmentRepository.class);
    private final IDeviceService devices = mock(IDeviceService.class);
    private final MediaAssetRepository media = mock(MediaAssetRepository.class);
    private final MediaUploadAttemptRepository attempts = mock(MediaUploadAttemptRepository.class);
    private final ManualUploadTaskRepository manualTasks = mock(ManualUploadTaskRepository.class);
    private final MediaAuditLogRepository audit = mock(MediaAuditLogRepository.class);
    private final IMediaObjectStorage storage = mock(IMediaObjectStorage.class);
    private final AuthenticatedUserResolver userResolver = mock(AuthenticatedUserResolver.class);
    private final com.ondemandmonitoring.mission.service.IMissionAuthorizationService authorization =
            mock(com.ondemandmonitoring.mission.service.IMissionAuthorizationService.class);
    private final MissionMediaAccessServiceImpl missionAccess = new MissionMediaAccessServiceImpl(
            missions, deviceAssignments, userResolver, authorization);
    private final MediaUploadServiceImpl service = new MediaUploadServiceImpl(missionAccess, devices,
            media, attempts, manualTasks, audit, storage, userResolver);

    @BeforeEach
    void setUp() {
        when(authorization.canViewMissionMedia("mission-id")).thenReturn(true);
        when(authorization.canOperatePayload("mission-id")).thenReturn(true);
        when(authorization.canUploadMissionMedia("mission-id")).thenReturn(true);
        ReflectionTestUtils.setField(service, "maxImageBytes", 25_000_000L);
        ReflectionTestUtils.setField(service, "maxVideoBytes", 1_000_000_000L);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("operator", "n/a", java.util.List.of()));
        User user = new User();
        user.setId(UUID.randomUUID().toString());
        when(userResolver.getCurrentUser()).thenReturn(user);
        when(userResolver.getCurrentUserId()).thenReturn(user.getId());
        Mission mission = new Mission();
        mission.setId("mission-id");
        mission.setStatus(MissionStatus.IN_FLIGHT);
        when(missions.findById("mission-id")).thenReturn(Optional.of(mission));
        MissionStaffAssignment staff = new MissionStaffAssignment();
        staff.setStaff(user);
        when(staffAssignments.findByMissionIdAndStaffIdAndIsCurrentTrue("mission-id", user.getId()))
                .thenReturn(Optional.of(staff));
        Device device = new Device();
        device.setId("device-id");
        device.setDeviceCode("DEVICE-01");
        when(devices.getEntityById("device-id")).thenReturn(device);
        when(devices.getEntityById("DEVICE-01")).thenReturn(device);
        MissionDeviceAssignment assignment = new MissionDeviceAssignment();
        assignment.setMission(mission);
        assignment.setDevice(device);
        when(deviceAssignments.findByMissionIdAndIsCurrentTrue("mission-id"))
                .thenReturn(Optional.of(assignment));
        when(deviceAssignments.findAllByMissionIdAndIsCurrentTrueOrderByCreatedAtDesc("mission-id"))
                .thenReturn(java.util.List.of(assignment));
        when(deviceAssignments.findByMissionIdAndDeviceIdAndIsCurrentTrue("mission-id", "device-id"))
                .thenReturn(Optional.of(assignment));
        when(storage.bucket()).thenReturn("test-bucket");
        when(storage.prefix()).thenReturn("");
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
        when(media.findByMissionIdAndDeviceIdAndLocalMediaId("mission-id", "device-id", "capture-1"))
                .thenReturn(Optional.of(existing));
        MediaUploadAttempt attempt = new MediaUploadAttempt();
        attempt.setId("attempt-id");
        attempt.setAttemptNumber(1);
        attempt.setStorageKey("staging/media-id/1.jpg");
        when(attempts.findFirstByMediaIdOrderByAttemptNumberDesc("media-id"))
                .thenReturn(Optional.of(attempt));
        when(storage.createPresignedPutUrl(any(), any(), anyLong(), any()))
                .thenReturn(new IMediaObjectStorage.PresignedUpload("https://s3.example/upload", Map.of(), 900));

        var result = service.prepare("mission-id", request);

        assertThat(result.getMediaId()).isEqualTo("media-id");
        assertThat(result.getAttemptId()).isEqualTo("attempt-id");
        verify(media, never()).saveAndFlush(any());
    }

    @Test
    void completedMissionAllowsItsReleasedOperatorAndDrone() {
        Mission mission = missions.findById("mission-id").orElseThrow();
        mission.setStatus(MissionStatus.COMPLETED);
        when(staffAssignments.findByMissionIdAndStaffIdAndIsCurrentTrue(
                "mission-id", userResolver.getCurrentUserId()))
                .thenReturn(Optional.empty());
        when(deviceAssignments.findByMissionIdAndIsCurrentTrue("mission-id"))
                .thenReturn(Optional.empty());
        MissionStaffAssignment staff = new MissionStaffAssignment();
        staff.setStaff(userResolver.getCurrentUser());
        staff.setReleaseReason("MISSION_COMPLETE");
        when(staffAssignments.findByMissionId("mission-id")).thenReturn(List.of(staff));
        MissionDeviceAssignment assignment = new MissionDeviceAssignment();
        assignment.setMission(mission);
        assignment.setDevice(devices.getEntityById("device-id"));
        assignment.setReleaseReason("MISSION_COMPLETE");
        when(deviceAssignments.findByMissionId("mission-id")).thenReturn(List.of(assignment));
        MediaAsset existing = new MediaAsset();
        existing.setId("media-id");
        existing.setMissionId("mission-id");
        existing.setType("IMAGE");
        existing.setContentType("image/jpeg");
        existing.setFileSize(100L);
        existing.setChecksumSha256("a".repeat(64));
        existing.setMediaStatus(MediaStatus.AVAILABLE);
        when(media.findByMissionIdAndDeviceIdAndLocalMediaId("mission-id", "device-id", "capture-1"))
                .thenReturn(Optional.of(existing));
        when(media.findById("media-id")).thenReturn(Optional.of(existing));

        assertThat(service.prepare("mission-id", request("IMAGE", "image/jpeg")).getStatus())
                .isEqualTo(MediaStatus.AVAILABLE);
    }

    private PrepareMediaUploadRequest request(String type, String contentType) {
        return new PrepareMediaUploadRequest("device-id", "capture-1", type, "capture.jpg",
                contentType, 100L, "a".repeat(64), Instant.now());
    }
}
