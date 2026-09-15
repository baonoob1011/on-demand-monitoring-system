package com.ondemandmonitoring.media.service;

import com.ondemandmonitoring.common.exception.ApiException;
import com.ondemandmonitoring.common.exception.ErrorCode;
import com.ondemandmonitoring.device.domain.Device;
import com.ondemandmonitoring.device.repository.DeviceRepository;
import com.ondemandmonitoring.media.domain.ManualUploadTask;
import com.ondemandmonitoring.media.domain.ManualUploadTaskStatus;
import com.ondemandmonitoring.media.domain.MediaAsset;
import com.ondemandmonitoring.media.domain.MediaNotificationOutbox;
import com.ondemandmonitoring.media.domain.MediaStatus;
import com.ondemandmonitoring.media.domain.MediaUploadAttempt;
import com.ondemandmonitoring.media.domain.StorageEventInbox;
import com.ondemandmonitoring.media.domain.UploadAttemptStatus;
import com.ondemandmonitoring.media.dto.MediaUploadResponse;
import com.ondemandmonitoring.media.dto.PrepareMediaUploadRequest;
import com.ondemandmonitoring.media.dto.ReportUploadFailureRequest;
import com.ondemandmonitoring.media.event.StorageObjectCreatedEvent;
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
import com.ondemandmonitoring.s3.S3ObjectStorageService.PresignedUpload;
import com.ondemandmonitoring.user.service.IUserService;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class MediaUploadService {

    private static final Set<MissionStatus> UPLOADABLE_MISSION_STATUSES = Set.of(
            MissionStatus.IN_FLIGHT, MissionStatus.IN_PROGRESS, MissionStatus.RETURNING);
    private static final int MAX_UPLOAD_ATTEMPTS = 3;
    private static final String CUSTOMER_MEDIA_AVAILABLE = "CUSTOMER_MEDIA_AVAILABLE";

    private final MissionRepository missionRepository;
    private final DeviceRepository deviceRepository;
    private final MediaAssetRepository mediaRepository;
    private final MediaUploadAttemptRepository attemptRepository;
    private final ManualUploadTaskRepository manualTaskRepository;
    private final MediaNotificationOutboxRepository notificationOutboxRepository;
    private final StorageEventInboxRepository storageEventInboxRepository;
    private final S3ObjectStorageService objectStorage;
    private final AwsS3Properties s3Properties;
    private final IUserService userService;

    @Value("${app.media.max-image-bytes:26214400}")
    private long maxImageBytes;

    @Value("${app.media.max-video-bytes:1073741824}")
    private long maxVideoBytes;

    @Transactional
    public MediaUploadResponse prepare(String missionId, PrepareMediaUploadRequest request) {
        Mission mission = requireUploadableMission(missionId);
        authorize(mission);
        Device device = requireAssignedDrone(mission, request.droneId());
        validateMetadata(request);

        return mediaRepository.findByMissionIdAndDeviceIdAndLocalMediaId(
                        mission.getId(), device.getId(), request.localMediaId())
                .map(existing -> idempotentResponse(existing, request))
                .orElseGet(() -> createMediaAndAttempt(mission, device, request));
    }

    @Transactional
    public MediaUploadResponse retry(String mediaId) {
        MediaAsset media = requireMedia(mediaId);
        authorize(requireUploadableMission(media.getMissionId()));
        if (media.getMediaStatus() == null || media.getMediaStatus() == MediaStatus.AVAILABLE) {
            throw new ApiException(ErrorCode.MEDIA_UPLOAD_ATTEMPT_INVALID, "Media is already available");
        }
        if (attemptCount(media) >= MAX_UPLOAD_ATTEMPTS) {
            return manualUploadRequired(media, "Automatic upload attempts exhausted");
        }
        return createAttempt(media, false);
    }

    @Transactional
    public MediaUploadResponse prepareManualUpload(String mediaId) {
        MediaAsset media = requireMedia(mediaId);
        authorize(requireUploadableMission(media.getMissionId()));
        if (media.getMediaStatus() != MediaStatus.MANUAL_UPLOAD_REQUIRED) {
            throw new ApiException(ErrorCode.MEDIA_UPLOAD_ATTEMPT_INVALID,
                    "Manual upload is only available after automatic retries are exhausted");
        }
        return createAttempt(media, true);
    }

    @Transactional
    public MediaUploadResponse reportFailure(
            String mediaId, String attemptId, ReportUploadFailureRequest request) {
        MediaAsset media = requireMedia(mediaId);
        authorize(findMission(media.getMissionId()));
        MediaUploadAttempt attempt = requireAttempt(mediaId, attemptId);
        if (attempt.getStatus() == UploadAttemptStatus.SUCCEEDED) {
            throw new ApiException(ErrorCode.MEDIA_UPLOAD_ATTEMPT_INVALID, "Upload attempt already succeeded");
        }
        attempt.setStatus(UploadAttemptStatus.FAILED);
        attempt.setFailureCode(request.code());
        attempt.setFailureMessage(request.message());
        attempt.setCompletedAt(Instant.now());
        attemptRepository.save(attempt);

        if (attemptCount(media) >= MAX_UPLOAD_ATTEMPTS) {
            return manualUploadRequired(media, request.message());
        }
        media.setMediaStatus(MediaStatus.RETRY_REQUIRED);
        media.setValidationError(request.message());
        mediaRepository.save(media);
        return createAttempt(media, false);
    }

    @Transactional
    public void markUploaded(String mediaId, String attemptId) {
        MediaAsset media = requireMedia(mediaId);
        authorize(findMission(media.getMissionId()));
        MediaUploadAttempt attempt = requireAttempt(mediaId, attemptId);
        if (attempt.getStatus() != UploadAttemptStatus.PENDING) {
            throw new ApiException(ErrorCode.MEDIA_UPLOAD_ATTEMPT_INVALID,
                    "Only a pending upload attempt can be completed");
        }
        attempt.setStatus(UploadAttemptStatus.UPLOADED);
        attemptRepository.save(attempt);
        media.setMediaStatus(MediaStatus.VALIDATING);
        mediaRepository.save(media);
    }

    @Transactional(readOnly = true)
    public MediaUploadResponse getStatus(String mediaId) {
        MediaAsset media = requireMedia(mediaId);
        authorize(findMission(media.getMissionId()));
        return new MediaUploadResponse(
                media.getId(), null, attemptCount(media),
                media.getMediaStatus() == null ? MediaStatus.AVAILABLE : media.getMediaStatus(),
                null, Map.of(), null,
                manualTaskRepository.findByMediaId(mediaId).map(ManualUploadTask::getId).orElse(null));
    }

    @Transactional
    public void processObjectCreated(String bucket, String key, Long eventSize) {
        processObjectCreated(new StorageObjectCreatedEvent(
                bucket, key, eventSize, null, null, null, "ObjectCreated:Manual", null));
    }

    @Transactional
    public void processObjectCreated(StorageObjectCreatedEvent event) {
        String eventKey = storageEventKey(event);
        if (storageEventInboxRepository.existsByEventKey(eventKey)) {
            log.info("Duplicate media storage event ignored. eventKey={}, bucket={}, key={}",
                    eventKey, event.bucket(), event.key());
            return;
        }

        MediaAsset media = mediaRepository.findByS3BucketAndS3Key(event.bucket(), event.key())
                .orElseThrow(() -> new ApiException(ErrorCode.MEDIA_NOT_FOUND,
                        "No pending media matches the storage object"));
        if (media.getMediaStatus() == null || media.getMediaStatus() == MediaStatus.AVAILABLE) {
            ensureCustomerNotification(media);
            recordProcessedStorageEvent(eventKey, event);
            return;
        }

        media.setMediaStatus(MediaStatus.VALIDATING);
        mediaRepository.save(media);
        String failure = validateStoredObject(media, event.size());
        MediaUploadAttempt attempt = latestAttempt(media);
        if (failure == null) {
            Instant now = Instant.now();
            media.setMediaStatus(MediaStatus.AVAILABLE);
            media.setValidatedAt(now);
            media.setAvailableAt(now);
            media.setValidationError(null);
            attempt.setStatus(UploadAttemptStatus.SUCCEEDED);
            attempt.setCompletedAt(now);
            attemptRepository.save(attempt);
            mediaRepository.save(media);
            manualTaskRepository.findByMediaId(media.getId()).ifPresent(task -> {
                task.setStatus(ManualUploadTaskStatus.RESOLVED);
                task.setResolvedAt(now);
                manualTaskRepository.save(task);
            });
            ensureCustomerNotification(media);
            recordProcessedStorageEvent(eventKey, event);
            log.info("Media became available. missionId={}, mediaId={}, attempts={}",
                    media.getMissionId(), media.getId(), attemptCount(media));
            return;
        }

        attempt.setStatus(UploadAttemptStatus.FAILED);
        attempt.setFailureCode("STORAGE_VALIDATION_FAILED");
        attempt.setFailureMessage(failure);
        attempt.setCompletedAt(Instant.now());
        attemptRepository.save(attempt);
        media.setValidationError(failure);
        if (attemptCount(media) >= MAX_UPLOAD_ATTEMPTS) {
            manualUploadRequired(media, failure);
        } else {
            media.setMediaStatus(MediaStatus.RETRY_REQUIRED);
            mediaRepository.save(media);
        }
        recordProcessedStorageEvent(eventKey, event);
    }

    private MediaUploadResponse createMediaAndAttempt(
            Mission mission, Device device, PrepareMediaUploadRequest request) {
        if (objectStorage.bucket() == null || objectStorage.bucket().isBlank()) {
            throw new ApiException(ErrorCode.INTERNAL_SERVER_ERROR,
                    "S3 bucket is required for direct media upload");
        }
        MediaAsset media = new MediaAsset();
        media.setDeviceCode(device.getDeviceCode());
        media.setDevice(device);
        media.setMissionId(mission.getId());
        media.setLocalMediaId(request.localMediaId());
        media.setType(request.mediaType());
        media.setStorageProvider("S3");
        media.setOriginalFileName(safeFileName(request.fileName()));
        media.setContentType(request.contentType());
        media.setFileSize(request.fileSize());
        media.setChecksumSha256(request.checksumSha256().toLowerCase());
        media.setCapturedAt(request.capturedAt());
        media.setMediaStatus(MediaStatus.UPLOAD_PENDING);
        media.setUploadAttemptCount(0);
        media.setS3Bucket(objectStorage.bucket());
        String pendingKey = "pending/" + UUID.randomUUID();
        media.setS3Key(pendingKey);
        media.setS3Url("s3://" + objectStorage.bucket() + "/" + pendingKey);
        media = mediaRepository.save(media);

        String key = buildStorageKey(media);
        media.setS3Key(key);
        media.setS3Url("s3://" + objectStorage.bucket() + "/" + key);
        mediaRepository.save(media);
        return createAttempt(media, false);
    }

    private MediaUploadResponse createAttempt(MediaAsset media, boolean manual) {
        if (!Objects.equals(media.getS3Bucket(), objectStorage.bucket())) {
            throw new ApiException(ErrorCode.MEDIA_UPLOAD_ATTEMPT_INVALID,
                    "Media was prepared for another S3 bucket; capture it again before uploading");
        }
        int number = attemptCount(media) + 1;
        PresignedUpload upload = objectStorage.createPresignedPutUrl(
                media.getS3Key(), media.getContentType(), media.getFileSize(),
                Map.of("media-id", media.getId(), "sha256", media.getChecksumSha256()));
        Instant expiresAt = Instant.now().plusSeconds(upload.expiresInSeconds());

        MediaUploadAttempt attempt = new MediaUploadAttempt();
        attempt.setMedia(media);
        attempt.setAttemptNumber(number);
        attempt.setStatus(UploadAttemptStatus.PENDING);
        attempt.setExpiresAt(expiresAt);
        attempt.setManualAttempt(manual);
        attempt = attemptRepository.save(attempt);

        media.setUploadAttemptCount(number);
        media.setMediaStatus(MediaStatus.UPLOAD_PENDING);
        mediaRepository.save(media);
        log.info("Media upload attempt prepared. actor={}, missionId={}, mediaId={}, attempt={}, manual={}",
                currentActor(), media.getMissionId(), media.getId(), number, manual);
        return response(media, attempt, upload, expiresAt);
    }

    private MediaUploadResponse idempotentResponse(MediaAsset media, PrepareMediaUploadRequest request) {
        boolean same = media.getType().equals(request.mediaType())
                && media.getContentType().equals(request.contentType())
                && media.getFileSize().equals(request.fileSize())
                && media.getChecksumSha256().equalsIgnoreCase(request.checksumSha256());
        if (!same) {
            throw new ApiException(ErrorCode.MEDIA_IDEMPOTENCY_CONFLICT);
        }
        if (media.getMediaStatus() == MediaStatus.UPLOAD_PENDING) {
            MediaUploadAttempt attempt = latestAttempt(media);
            PresignedUpload upload = objectStorage.createPresignedPutUrl(
                    media.getS3Key(), media.getContentType(), media.getFileSize(),
                    Map.of("media-id", media.getId(), "sha256", media.getChecksumSha256()));
            Instant expiresAt = Instant.now().plusSeconds(upload.expiresInSeconds());
            attempt.setExpiresAt(expiresAt);
            attemptRepository.save(attempt);
            return response(media, attempt, upload, expiresAt);
        }
        return getStatus(media.getId());
    }

    private MediaUploadResponse manualUploadRequired(MediaAsset media, String reason) {
        media.setMediaStatus(MediaStatus.MANUAL_UPLOAD_REQUIRED);
        media.setValidationError(reason);
        mediaRepository.save(media);
        ManualUploadTask task = manualTaskRepository.findByMediaId(media.getId()).orElseGet(() -> {
            ManualUploadTask created = new ManualUploadTask();
            created.setMedia(media);
            created.setStatus(ManualUploadTaskStatus.OPEN);
            created.setAssignedOperatorId(missionRepository.findById(media.getMissionId())
                    .map(Mission::getOperatorId)
                    .orElse(null));
            return created;
        });
        task.setReason(reason);
        task = manualTaskRepository.save(task);
        return new MediaUploadResponse(media.getId(), null, attemptCount(media),
                media.getMediaStatus(), null, Map.of(), null, task.getId());
    }

    private String validateStoredObject(MediaAsset media, Long eventSize) {
        if (eventSize != null && !media.getFileSize().equals(eventSize)) {
            return "Object size does not match prepared metadata";
        }
        var storedObject = objectStorage.open(media.getS3Bucket(), media.getS3Key());
        try (InputStream raw = storedObject.inputStream()) {
            if (storedObject.contentLength() != null
                    && !media.getFileSize().equals(storedObject.contentLength())) {
                return "Stored object size does not match prepared metadata";
            }
            if (storedObject.contentType() != null
                    && !media.getContentType().equalsIgnoreCase(storedObject.contentType())) {
                return "Stored object content type does not match prepared metadata";
            }
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] prefix = raw.readNBytes(12);
            digest.update(prefix);
            try (DigestInputStream input = new DigestInputStream(raw, digest)) {
                input.transferTo(OutputStream.nullOutputStream());
            }
            if (!validSignature(media.getContentType(), prefix)) {
                return "File signature does not match content type";
            }
            if (!HexFormat.of().formatHex(digest.digest()).equalsIgnoreCase(media.getChecksumSha256())) {
                return "SHA-256 checksum mismatch";
            }
            return null;
        } catch (IOException | NoSuchAlgorithmException exception) {
            return "Cannot validate stored object: " + exception.getMessage();
        }
    }

    private void ensureCustomerNotification(MediaAsset media) {
        if (notificationOutboxRepository.existsByMediaIdAndEventType(
                media.getId(), CUSTOMER_MEDIA_AVAILABLE)) {
            return;
        }
        MediaNotificationOutbox outbox = new MediaNotificationOutbox();
        outbox.setMissionId(media.getMissionId());
        outbox.setMediaId(media.getId());
        outbox.setEventType(CUSTOMER_MEDIA_AVAILABLE);
        outbox.setStatus("PENDING");
        notificationOutboxRepository.save(outbox);
        log.info("Customer media notification enqueued. missionId={}, mediaId={}",
                media.getMissionId(), media.getId());
    }

    private void recordProcessedStorageEvent(String eventKey, StorageObjectCreatedEvent event) {
        StorageEventInbox inbox = new StorageEventInbox();
        inbox.setEventKey(eventKey);
        inbox.setBucket(event.bucket());
        inbox.setObjectKey(event.key());
        inbox.setEventName(event.eventName());
        inbox.setSourceEventId(event.eventId());
        inbox.setObjectVersionId(event.versionId());
        inbox.setSequencer(event.sequencer());
        inbox.setSourceEventTime(event.eventTime());
        inbox.setProcessedAt(Instant.now());
        storageEventInboxRepository.save(inbox);
    }

    private String storageEventKey(StorageObjectCreatedEvent event) {
        String sourceIdentity = firstNonBlank(
                event.eventId(), event.versionId(), event.sequencer(), event.eventName(), "object-created");
        String material = event.bucket() + "\n" + event.key() + "\n" + sourceIdentity;
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(material.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        throw new IllegalArgumentException("Storage event identity is required");
    }

    private boolean validSignature(String contentType, byte[] bytes) {
        if ("image/jpeg".equals(contentType)) {
            return bytes.length >= 3 && (bytes[0] & 0xff) == 0xff && (bytes[1] & 0xff) == 0xd8
                    && (bytes[2] & 0xff) == 0xff;
        }
        if ("image/png".equals(contentType)) {
            byte[] png = {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};
            return bytes.length >= png.length && java.util.Arrays.equals(png, java.util.Arrays.copyOf(bytes, png.length));
        }
        return "video/mp4".equals(contentType) && bytes.length >= 8
                && bytes[4] == 'f' && bytes[5] == 't' && bytes[6] == 'y' && bytes[7] == 'p';
    }

    private void validateMetadata(PrepareMediaUploadRequest request) {
        if ("IMAGE".equals(request.mediaType()) && request.contentType().equals("video/mp4")) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "IMAGE media cannot use video/mp4");
        }
        if ("VIDEO".equals(request.mediaType()) && !request.contentType().equals("video/mp4")) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "VIDEO media must use video/mp4");
        }
        long maximum = "IMAGE".equals(request.mediaType()) ? maxImageBytes : maxVideoBytes;
        if (request.fileSize() > maximum) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "Media file exceeds the configured size limit");
        }
    }

    private Mission requireUploadableMission(String missionId) {
        Mission mission = findMission(missionId);
        if (!UPLOADABLE_MISSION_STATUSES.contains(mission.getStatus())) {
            throw new ApiException(ErrorCode.MEDIA_UPLOAD_NOT_ALLOWED,
                    "Mission must be IN_FLIGHT, IN_PROGRESS, or RETURNING");
        }
        return mission;
    }

    private Mission findMission(String missionId) {
        Mission mission = missionRepository.findById(missionId)
                .or(() -> missionRepository.findByMissionCode(missionId))
                .orElseThrow(() -> new ApiException(ErrorCode.MISSION_NOT_FOUND));
        return mission;
    }

    private void authorize(Mission mission) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ApiException(ErrorCode.UNAUTHORIZED);
        }
        boolean privileged = authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_ADMIN")
                        || authority.getAuthority().equals("ROLE_SYSTEM_OPERATOR"));
        String authenticatedOperatorId = privileged
                ? null
                : userService.findByCognitoSub(authentication.getName()).getId().toString();
        if (!privileged && (mission.getOperatorId() == null
                || !mission.getOperatorId().equals(authenticatedOperatorId))) {
            throw new ApiException(ErrorCode.ACCESS_DENIED,
                    "Operator is not assigned to this mission");
        }
    }

    private String currentActor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication == null ? "storage-event" : authentication.getName();
    }

    private Device requireAssignedDrone(Mission mission, String droneId) {
        Device device = deviceRepository.findByDeviceCode(droneId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Drone not found"));
        if (mission.getDevice() == null || !mission.getDevice().getId().equals(device.getId())) {
            throw new ApiException(ErrorCode.ACCESS_DENIED, "Drone is not assigned to this mission");
        }
        return device;
    }

    private MediaAsset requireMedia(String mediaId) {
        return mediaRepository.findById(mediaId)
                .orElseThrow(() -> new ApiException(ErrorCode.MEDIA_NOT_FOUND));
    }

    private MediaUploadAttempt requireAttempt(String mediaId, String attemptId) {
        return attemptRepository.findByIdAndMediaId(attemptId, mediaId)
                .orElseThrow(() -> new ApiException(ErrorCode.MEDIA_UPLOAD_ATTEMPT_INVALID));
    }

    private MediaUploadAttempt latestAttempt(MediaAsset media) {
        return attemptRepository.findTopByMediaIdOrderByAttemptNumberDesc(media.getId())
                .orElseThrow(() -> new ApiException(ErrorCode.MEDIA_UPLOAD_ATTEMPT_INVALID));
    }

    private MediaUploadResponse response(
            MediaAsset media, MediaUploadAttempt attempt, PresignedUpload upload, Instant expiresAt) {
        return new MediaUploadResponse(media.getId(), attempt.getId(), attempt.getAttemptNumber(),
                media.getMediaStatus(), upload.url(), upload.headers(), expiresAt, null);
    }

    private String buildStorageKey(MediaAsset media) {
        String prefix = s3Properties.getPrefix();
        String root = prefix == null || prefix.isBlank() ? "" : safeSegment(prefix) + "/";
        String extension = switch (media.getContentType()) {
            case "image/png" -> ".png";
            case "video/mp4" -> ".mp4";
            default -> ".jpg";
        };
        return root + "missions/" + safeSegment(media.getMissionId()) + "/drones/"
                + safeSegment(media.getDeviceCode()) + "/" + media.getType().toLowerCase() + "s/"
                + media.getId() + extension;
    }

    private String safeFileName(String value) {
        return value.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private String safeSegment(String value) {
        return value.strip().replaceAll("^/+|/+$", "").replaceAll("[^A-Za-z0-9._/-]", "_");
    }

    private int attemptCount(MediaAsset media) {
        return media.getUploadAttemptCount() == null ? 0 : media.getUploadAttemptCount();
    }
}
