package com.ondemandmonitoring.media.service.impl;

import com.ondemandmonitoring.media.domain.*;
import com.ondemandmonitoring.media.repository.*;
import com.ondemandmonitoring.media.service.IMediaValidationService;
import com.ondemandmonitoring.s3.S3ObjectStorageService;
import com.ondemandmonitoring.media.service.IMediaAuditService;
import com.ondemandmonitoring.media.service.IMediaObjectVerificationService;
import com.ondemandmonitoring.media.policy.MediaUploadPolicy;
import com.ondemandmonitoring.media.enums.*;
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

    static String AVAILABLE_EVENT = "CUSTOMER_MEDIA_AVAILABLE";

    MediaUploadAttemptRepository attempts;
    MediaAssetRepository media;
    StorageEventInboxRepository inbox;
    ManualUploadTaskRepository manualTasks;
    IMediaAuditService auditService;
    MediaUploadPolicy policy;
    MediaNotificationOutboxRepository outbox;
    S3ObjectStorageService storage;
    IMediaObjectVerificationService verification;

    @Override
    @Transactional
    public void processObjectCreated(String bucket, String key, String eventIdentity) {
        String eventKey = sha256((bucket + "\n" + key + "\n" + eventIdentity).getBytes());

        if (inbox.existsByEventKey(eventKey)) {
            return;
        }

        if (!storage.bucket().equals(bucket)) {
            return;
        }
        Optional<String> mediaId = attempts.findMediaIdByStorageKey(key);
        if (mediaId.isEmpty()) {
            log.info("Ignoring unrelated storage event bucket={} key={}", bucket, key);
            return;
        }
        // Lock aggregate first, then attempt; upload callbacks use the same order.
        MediaAsset captured = media.findByIdForUpdate(mediaId.get()).orElseThrow();
        Optional<MediaUploadAttempt> found = attempts.findByStorageKeyForUpdate(key);

        if (found.isEmpty()) {
            log.info("Ignoring unrelated storage event bucket={} key={}", bucket, key);
            return;
        }

        MediaUploadAttempt attempt = found.get();
        // SQS and reconciliation can wait on the same row concurrently.
        if (inbox.existsByEventKey(eventKey)) {
            return;
        }
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

        String failure = verification.verify(captured, key);

        if (failure != null) {
            attempt.setStatus(UploadAttemptStatus.FAILED);
            attempt.setFailureCode(MediaFailureCode.STORAGE_VALIDATION_FAILED.name());
            attempt.setFailureMessage(failure);
            attempt.setCompletedAt(Instant.now());
            attempts.save(attempt);
            captured.setValidationError(failure);
            if (policy.failureStatus(attempt.isManualAttempt(),
                    attempts.countByMediaIdAndManualAttemptFalse(captured.getId()))
                    == MediaStatus.MANUAL_UPLOAD_REQUIRED) {
                requireManualUpload(captured, failure);
            } else {
                captured.setMediaStatus(MediaStatus.RETRY_REQUIRED);
            }
            media.save(captured);
            audit(captured, attempt, MediaAuditAction.VALIDATION_FAILED, failure);
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
            task.setStatus(ManualUploadTaskStatus.RESOLVED);
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

        audit(captured, attempt, MediaAuditAction.AVAILABLE, null);
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
                       MediaAuditAction action, String detail) {
        auditService.record(captured, attempt, "s3-event-consumer", action, detail);
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
