package com.ondemandmonitoring.media.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ondemandmonitoring.media.domain.MediaAsset;
import com.ondemandmonitoring.media.domain.MediaNotificationOutbox;
import com.ondemandmonitoring.media.domain.MediaStatus;
import com.ondemandmonitoring.media.domain.MediaUploadAttempt;
import com.ondemandmonitoring.media.domain.StorageEventInbox;
import com.ondemandmonitoring.media.domain.UploadAttemptStatus;
import com.ondemandmonitoring.media.repository.ManualUploadTaskRepository;
import com.ondemandmonitoring.media.repository.MediaAssetRepository;
import com.ondemandmonitoring.media.repository.MediaAuditLogRepository;
import com.ondemandmonitoring.media.repository.MediaNotificationOutboxRepository;
import com.ondemandmonitoring.media.repository.MediaUploadAttemptRepository;
import com.ondemandmonitoring.media.repository.StorageEventInboxRepository;
import com.ondemandmonitoring.media.service.impl.MediaValidationServiceImpl;
import com.ondemandmonitoring.s3.S3ObjectStorageService;
import java.io.ByteArrayInputStream;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MediaValidationServiceImplTest {

    @Mock MediaUploadAttemptRepository attempts;
    @Mock MediaAssetRepository media;
    @Mock StorageEventInboxRepository inbox;
    @Mock ManualUploadTaskRepository manualTasks;
    @Mock MediaAuditLogRepository auditLogs;
    @Mock MediaNotificationOutboxRepository outbox;
    @Mock S3ObjectStorageService storage;
    @InjectMocks MediaValidationServiceImpl service;

    @Test
    void successfulValidationPersistsMissionAndBucketForLegacySchema() throws Exception {
        String bucket = "media-bucket";
        String stagingKey = "drone-media/staging/missions/mission-1/drones/DRN-1/media-1/1.jpg";
        byte[] image = {(byte) 0xff, (byte) 0xd8, (byte) 0xff};
        String checksum = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(image));

        MediaAsset captured = new MediaAsset();
        captured.setId("media-1");
        captured.setMissionId("mission-1");
        captured.setS3Bucket(bucket);
        captured.setFileSize((long) image.length);
        captured.setContentType("image/jpeg");
        captured.setChecksumSha256(checksum);

        MediaUploadAttempt attempt = new MediaUploadAttempt();
        attempt.setId("attempt-1");
        attempt.setMedia(captured);
        attempt.setStatus(UploadAttemptStatus.UPLOADED);

        when(storage.bucket()).thenReturn(bucket);
        when(attempts.findByStorageKey(stagingKey)).thenReturn(Optional.of(attempt));
        when(attempts.findFirstByMediaIdOrderByAttemptNumberDesc("media-1"))
                .thenReturn(Optional.of(attempt));
        when(storage.inspect(bucket, stagingKey)).thenReturn(
                new S3ObjectStorageService.StoredObjectInfo(
                        (long) image.length, "image/jpeg",
                        Map.of("media-id", "media-1", "sha256", checksum)));
        when(storage.open(bucket, stagingKey)).thenReturn(
                new S3ObjectStorageService.StoredObjectStream(
                        new ByteArrayInputStream(image), (long) image.length, "image/jpeg"));

        service.processObjectCreated(bucket, stagingKey, "event-1");

        ArgumentCaptor<MediaNotificationOutbox> notification =
                ArgumentCaptor.forClass(MediaNotificationOutbox.class);
        verify(outbox).save(notification.capture());
        assertThat(notification.getValue().getMissionId()).isEqualTo("mission-1");
        assertThat(notification.getValue().getMedia()).isSameAs(captured);

        ArgumentCaptor<StorageEventInbox> event = ArgumentCaptor.forClass(StorageEventInbox.class);
        verify(inbox).save(event.capture());
        assertThat(event.getValue().getBucket()).isEqualTo(bucket);
        assertThat(captured.getMediaStatus()).isEqualTo(MediaStatus.AVAILABLE);
        assertThat(attempt.getStatus()).isEqualTo(UploadAttemptStatus.SUCCEEDED);
        verify(storage).copy(bucket, stagingKey, stagingKey.replace("/staging/", "/final/"));
    }
}
