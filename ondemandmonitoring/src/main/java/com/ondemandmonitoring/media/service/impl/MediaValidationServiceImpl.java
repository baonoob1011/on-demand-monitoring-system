package com.ondemandmonitoring.media.service.impl;

import com.ondemandmonitoring.media.domain.*;
import com.ondemandmonitoring.media.repository.*;
import com.ondemandmonitoring.media.service.IMediaValidationService;
import com.ondemandmonitoring.s3.S3ObjectStorageService;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.*;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
@RequiredArgsConstructor
@Slf4j
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class MediaValidationServiceImpl implements IMediaValidationService {

    static int MAX_AUTOMATIC_ATTEMPTS = 3;
    static String AVAILABLE_EVENT = "CUSTOMER_MEDIA_AVAILABLE";

    MediaUploadAttemptRepository attempts;
    MediaAssetRepository media;
    StorageEventInboxRepository inbox;
    ManualUploadTaskRepository manualTasks;
    MediaAuditLogRepository auditLogs;
    MediaNotificationOutboxRepository outbox;
    S3ObjectStorageService storage;

    @Override
    @Transactional
    public void processObjectCreated(String bucket, String key, String eventIdentity) {
        String eventKey = sha256((bucket + "\n" + key + "\n" + eventIdentity).getBytes());

        if (inbox.existsByEventKey(eventKey)) {
            return;
        }

        Optional<MediaUploadAttempt> found = attempts.findByStorageKeyForUpdate(key);

        if (found.isEmpty() || !storage.bucket().equals(bucket)) {
            log.info("Ignoring unrelated storage event bucket={} key={}", bucket, key);
            return;
        }

        MediaUploadAttempt attempt = found.get();
        // SQS and reconciliation can wait on the same row concurrently.
        if (inbox.existsByEventKey(eventKey)) {
            return;
        }
        MediaAsset captured = attempt.getMedia();
        boolean latest = attempts.findFirstByMediaIdOrderByAttemptNumberDesc(captured.getId())
                .map(current -> current.getId().equals(attempt.getId())).orElse(false);

        if (!latest || attempt.getStatus() == UploadAttemptStatus.FAILED) {
            recordEvent(eventKey, bucket, key);
            return;
        }

        if (attempt.getStatus() == UploadAttemptStatus.SUCCEEDED) {
            recordEvent(eventKey, bucket, key);
            return;
        }

        String failure = verifyObject(captured, key);

        if (failure != null) {
            attempt.setStatus(UploadAttemptStatus.FAILED);
            attempt.setFailureCode("STORAGE_VALIDATION_FAILED");
            attempt.setFailureMessage(failure);
            attempt.setCompletedAt(Instant.now());
            attempts.save(attempt);
            captured.setValidationError(failure);
            if (attempts.countByMediaIdAndManualAttemptFalse(captured.getId())
                    >= MAX_AUTOMATIC_ATTEMPTS) {
                requireManualUpload(captured, failure);
            } else {
                captured.setMediaStatus(MediaStatus.RETRY_REQUIRED);
            }
            media.save(captured);
            audit(captured, attempt, "VALIDATION_FAILED", failure);
            recordEvent(eventKey, bucket, key);
            return;
        }

        String finalKey = key.replaceFirst("(^|/)staging/", "$1final/");

        if (finalKey.equals(key)) {
            throw new IllegalStateException("Attempt key is outside staging prefix");
        }

        storage.copy(bucket, key, finalKey);
        Instant now = Instant.now();
        captured.setS3Key(finalKey);
        captured.setS3Url("s3://" + bucket + "/" + finalKey);
        captured.setMediaStatus(MediaStatus.AVAILABLE);
        captured.setValidationError(null);
        captured.setValidatedAt(now);
        captured.setAvailableAt(now);
        attempt.setStatus(UploadAttemptStatus.SUCCEEDED);
        attempt.setCompletedAt(now);
        attempts.save(attempt);
        media.save(captured);
        manualTasks.findByMediaId(captured.getId()).ifPresent(task -> {
            task.setStatus("RESOLVED");
            task.setResolvedAt(now);
            manualTasks.save(task);
        });

        if (!outbox.existsByMediaIdAndEventType(captured.getId(), AVAILABLE_EVENT)) {
            MediaNotificationOutbox notification = new MediaNotificationOutbox();
            notification.setMedia(captured);
            notification.setMissionId(captured.getMissionId());
            notification.setEventType(AVAILABLE_EVENT);
            outbox.save(notification);
        }

        audit(captured, attempt, "AVAILABLE", null);
        recordEvent(eventKey, bucket, key);

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    storage.deleteQuietly(bucket, key);
                }
            });
        }
    }

    private String verifyObject(MediaAsset captured, String key) {
        var object = storage.inspect(captured.getS3Bucket(), key);

        if (!captured.getFileSize().equals(object.contentLength())) {
            return "Object size differs from capture metadata";
        }

        if (!captured.getContentType().equalsIgnoreCase(object.contentType())) {
            return "Object content type differs from capture metadata";
        }

        if (!captured.getId().equals(object.metadata().get("media-id"))
                || !captured.getChecksumSha256().equalsIgnoreCase(object.metadata().get("sha256"))) {
            return "Object metadata differs from upload request";
        }

        try (InputStream stream = storage.open(captured.getS3Bucket(), key).inputStream()) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] signature = stream.readNBytes(12);
            digest.update(signature);
            byte[] buffer = new byte[64 * 1024];
            int read;

            while ((read = stream.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }

            if (!validSignature(captured.getContentType(), signature)) {
                return "File signature differs from declared content type";
            }

            if (!HexFormat.of().formatHex(digest.digest())
                    .equalsIgnoreCase(captured.getChecksumSha256())) {
                return "SHA-256 checksum mismatch";
            }

            return null;

        } catch (IOException | NoSuchAlgorithmException error) {
            throw new IllegalStateException("Cannot inspect uploaded object", error);
        }
    }

    private boolean validSignature(String contentType, byte[] value) {
        return switch (contentType) {
            case "image/jpeg" -> value.length >= 3 && (value[0] & 0xff) == 0xff
                    && (value[1] & 0xff) == 0xd8 && (value[2] & 0xff) == 0xff;
            case "image/png" -> value.length >= 8 && Arrays.equals(Arrays.copyOf(value, 8),
                    new byte[]{(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a});
            case "video/mp4" -> value.length >= 8 && value[4] == 'f' && value[5] == 't'
                    && value[6] == 'y' && value[7] == 'p';
            default -> false;
        };
    }

    private void requireManualUpload(MediaAsset captured, String reason) {
        captured.setMediaStatus(MediaStatus.MANUAL_UPLOAD_REQUIRED);
        ManualUploadTask task = manualTasks.findByMediaId(captured.getId()).orElseGet(() -> {
            ManualUploadTask created = new ManualUploadTask();
            created.setMedia(captured);
            created.setAssignedOperatorId(captured.getOperatorId());
            return created;
        });
        task.setReason(reason);
        manualTasks.save(task);
    }

    private void audit(MediaAsset captured, MediaUploadAttempt attempt,
                       String action, String detail) {
        MediaAuditLog entry = new MediaAuditLog();
        entry.setMedia(captured);
        entry.setAttemptId(attempt.getId());
        entry.setActorId("s3-event-consumer");
        entry.setAction(action);
        entry.setDetail(detail);
        auditLogs.save(entry);
    }

    private void recordEvent(String eventKey, String bucket, String key) {
        StorageEventInbox event = new StorageEventInbox();
        event.setEventKey(eventKey);
        event.setBucket(bucket);
        event.setObjectKey(key);
        event.setProcessedAt(Instant.now());
        inbox.save(event);
    }

    private String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest
                    .getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException(error);
        }
    }
}
