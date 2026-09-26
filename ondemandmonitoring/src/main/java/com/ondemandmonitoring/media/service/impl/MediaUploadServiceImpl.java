package com.ondemandmonitoring.media.service.impl;

import com.ondemandmonitoring.common.exception.ApiException;
import com.ondemandmonitoring.common.exception.ErrorCode;
import com.ondemandmonitoring.drone.domain.Drone;
import com.ondemandmonitoring.drone.service.IDroneService;
import com.ondemandmonitoring.media.domain.*;
import com.ondemandmonitoring.media.dto.request.CompleteMultipartRequest;
import com.ondemandmonitoring.media.dto.request.PrepareMediaUploadRequest;
import com.ondemandmonitoring.media.dto.request.ReportUploadFailureRequest;
import com.ondemandmonitoring.media.dto.response.MediaUploadResponse;
import com.ondemandmonitoring.media.dto.request.ManualMediaFileRequest;
import com.ondemandmonitoring.media.dto.response.ManualMediaUploadResponse;
import com.ondemandmonitoring.media.repository.*;
import com.ondemandmonitoring.media.service.IMediaUploadService;
import com.ondemandmonitoring.mission.dto.response.MissionMediaContext;
import com.ondemandmonitoring.mission.service.IMissionMediaAccessService;
import com.ondemandmonitoring.s3.AwsS3Properties;
import com.ondemandmonitoring.s3.S3ObjectStorageService;
import com.ondemandmonitoring.user.service.AuthenticatedUserResolver;
import java.time.Instant;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MediaUploadServiceImpl implements IMediaUploadService {

    private static final long PART_SIZE = 8L * 1024 * 1024;
    private static final long MULTIPART_THRESHOLD = 16L * 1024 * 1024;
    private static final int MAX_AUTOMATIC_ATTEMPTS = 3;
    private final IMissionMediaAccessService missionAccess;
    private final IDroneService drones;
    private final MediaAssetRepository media;
    private final MediaUploadAttemptRepository attempts;
    private final ManualUploadTaskRepository manualTasks;
    private final MediaAuditLogRepository auditLogs;
    private final S3ObjectStorageService storage;
    private final AwsS3Properties s3Properties;
    private final AuthenticatedUserResolver currentUser;

    @Value("${app.media.max-image-bytes:26214400}")
    private long maxImageBytes;
    @Value("${app.media.max-video-bytes:1073741824}")
    private long maxVideoBytes;

    @Override
    @Transactional(readOnly = true)
    public List<ManualMediaUploadResponse> manualTasks(String missionId) {
        MissionMediaContext mission = missionAccess.authorizeOperator(missionId);
        return manualTasks.findOpenByMissionId(mission.getId()).stream().map(task -> {
            MediaAsset asset = task.getMedia();
            return new ManualMediaUploadResponse(
                    task.getId(), asset.getId(), asset.getLocalMediaId(), asset.getMissionId(),
                    asset.getDroneCode(), asset.getType(), asset.getOriginalFileName(),
                    asset.getContentType(), asset.getFileSize(), asset.getChecksumSha256(),
                    asset.getCapturedAt(), asset.getMediaStatus().name(), task.getReason());
        }).toList();
    }

    @Override
    @Transactional
    public MediaUploadResponse prepareManualFile(String mediaId,
            ManualMediaFileRequest request) {
        MediaAsset asset = media.findByIdForUpdate(mediaId)
                .orElseThrow(() -> new ApiException(ErrorCode.MEDIA_NOT_FOUND));
        missionAccess.authorizeOperator(asset.getMissionId());
        if (!asset.getFileSize().equals(request.fileSize())
                || !asset.getContentType().equalsIgnoreCase(request.contentType())
                || !asset.getChecksumSha256().equalsIgnoreCase(request.checksumSha256())) {
            throw new ApiException(ErrorCode.MEDIA_IDEMPOTENCY_CONFLICT,
                    "Selected file is not an exact copy of the original capture");
        }
        if (manualTasks.findByMediaId(mediaId).isEmpty()) {
            throw new ApiException(ErrorCode.MEDIA_UPLOAD_ATTEMPT_INVALID, "No manual task exists");
        }
        if (asset.getMediaStatus() == MediaStatus.UPLOAD_PENDING) {
            var latest = attempts.findFirstByMediaIdOrderByAttemptNumberDesc(mediaId);
            if (latest.isPresent() && latest.get().isManualAttempt()
                    && objectExists(asset.getS3Bucket(), latest.get().getStorageKey())) {
                markUploaded(mediaId, latest.get().getId());
                return status(mediaId);
            }
        }
        MediaUploadResponse response = retry(mediaId, true);
        audit(asset, null, "PC_BACKUP_SELECTED", "Exact backup selected for manual upload");
        return response;
    }

    @Override
    @Transactional
    public MediaUploadResponse prepare(String missionId, PrepareMediaUploadRequest request) {
        MissionMediaContext mission = missionAccess.authorizeOperator(missionId);
        if (!mission.isCaptureAllowed()) {
            throw new ApiException(ErrorCode.MEDIA_UPLOAD_NOT_ALLOWED,
                    "Mission is not in a capture state");
        }
        Drone drone = requireAssignedDrone(mission, request.getDroneCode());
        validateMetadata(request);
        Optional<MediaAsset> existing = media.findByMissionIdAndDroneCodeAndLocalMediaId(
                mission.getId(), drone.getDroneCode(), request.getLocalMediaId());
        if (existing.isPresent()) {
            MediaAsset captured = existing.get();
            if (!captured.getType().equals(request.getMediaType())
                    || !captured.getContentType().equals(request.getContentType())
                    || !captured.getFileSize().equals(request.getFileSize())
                    || !captured.getChecksumSha256().equalsIgnoreCase(request.getChecksumSha256())) {
                throw new ApiException(ErrorCode.MEDIA_IDEMPOTENCY_CONFLICT);
            }
            return existingResponse(captured);
        }
        if (storage.bucket() == null || storage.bucket().isBlank()) {
            throw new ApiException(ErrorCode.INTERNAL_SERVER_ERROR, "S3 bucket is required");
        }
        MediaAsset captured = new MediaAsset();
        captured.setMissionId(mission.getId());
        captured.setDrone(drone);
        captured.setDroneCode(drone.getDroneCode());
        captured.setLocalMediaId(request.getLocalMediaId());
        captured.setOperatorId(actor());
        captured.setType(request.getMediaType());
        captured.setStorageProvider("S3");
        captured.setOriginalFileName(request.getFileName().replaceAll("[^A-Za-z0-9._-]", "_"));
        captured.setContentType(request.getContentType());
        captured.setFileSize(request.getFileSize());
        captured.setChecksumSha256(request.getChecksumSha256().toLowerCase(Locale.ROOT));
        captured.setCapturedAt(request.getCapturedAt());
        captured.setMediaStatus(MediaStatus.UPLOAD_PENDING);
        captured.setS3Bucket(storage.bucket());
        captured.setS3Key("staging/pending/" + UUID.randomUUID());
        captured.setS3Url("s3://" + storage.bucket() + "/" + captured.getS3Key());
        captured = media.saveAndFlush(captured);
        audit(captured, null, "PREPARED", null);

        return newAttempt(captured, false);
    }

    @Override
    @Transactional
    public MediaUploadResponse retry(String mediaId, boolean manual) {
        // Serialize attempt allocation across browser tabs and backend instances.
        MediaAsset captured = media.findByIdForUpdate(mediaId)
                .orElseThrow(() -> new ApiException(ErrorCode.MEDIA_NOT_FOUND));
        missionAccess.authorizeOperator(captured.getMissionId());

        if (captured.getMediaStatus() == MediaStatus.AVAILABLE) {
            return status(mediaId);
        }

        if (manual && captured.getMediaStatus() == MediaStatus.VALIDATING) {
            return status(mediaId);
        }
        if (manual && captured.getMediaStatus() == MediaStatus.UPLOAD_PENDING
                && attempts.findFirstByMediaIdOrderByAttemptNumberDesc(mediaId)
                .map(MediaUploadAttempt::isManualAttempt).orElse(false)) {
            return existingResponse(captured);
        }

        if (manual && captured.getMediaStatus() != MediaStatus.MANUAL_UPLOAD_REQUIRED) {
            throw new ApiException(ErrorCode.MEDIA_UPLOAD_ATTEMPT_INVALID,
                    "Manual upload is not required");
        }

        if (!manual && captured.getMediaStatus() != MediaStatus.RETRY_REQUIRED) {
            throw new ApiException(ErrorCode.MEDIA_UPLOAD_ATTEMPT_INVALID,
                    "Media is not ready for retry");
        }

        if (!manual && attempts.countByMediaIdAndManualAttemptFalse(mediaId) >= MAX_AUTOMATIC_ATTEMPTS) {
            return requireManualUpload(captured,
                    "Automatic attempts exhausted");
        }

        return newAttempt(captured, manual);
    }

    @Override
    @Transactional
    public MediaUploadResponse reportFailure(String mediaId,
                                             String attemptId,
                                             ReportUploadFailureRequest request) {

        MediaAsset captured = requireMedia(mediaId);
        missionAccess.authorizeOperator(captured.getMissionId());
        MediaUploadAttempt attempt = requireAttempt(mediaId, attemptId);

        if (attempt.getStatus() != UploadAttemptStatus.PENDING
                || captured.getMediaStatus() == MediaStatus.AVAILABLE
                || !attempts.findFirstByMediaIdOrderByAttemptNumberDesc(mediaId)
                .map(latest -> latest.getId().equals(attemptId)).orElse(false)) {
            return status(mediaId);
        }

        // A lost transfer response does not prove that S3 rejected the object.
        // Only a confirmed 404 allows us to consume a retry attempt.
        try {
            storage.inspect(captured.getS3Bucket(), attempt.getStorageKey());
            markUploaded(mediaId, attemptId);
            return status(mediaId);
        } catch (software.amazon.awssdk.services.s3.model.S3Exception exception) {
            if (exception.statusCode() != 404) {
                throw exception;
            }
        }

        attempt.setStatus(UploadAttemptStatus.FAILED);
        attempt.setFailureCode(request.getCode());
        attempt.setFailureMessage(request.getMessage());
        attempt.setCompletedAt(Instant.now());
        attempts.save(attempt);
        if (attempt.getMultipartUploadId() != null) {
            try {
                storage.abortMultipartUpload(
                        attempt.getStorageKey(),
                        attempt.getMultipartUploadId());
            } catch (RuntimeException ignored) {

            }
        }

        captured.setValidationError(request.getMessage());
        audit(captured, attempt, "UPLOAD_FAILED", request.getCode());
        if (attempt.isManualAttempt()
                || attempts.countByMediaIdAndManualAttemptFalse(mediaId) >= MAX_AUTOMATIC_ATTEMPTS) {
            return requireManualUpload(captured, request.getMessage());
        }

        captured.setMediaStatus(MediaStatus.RETRY_REQUIRED);
        media.save(captured);

        return status(mediaId);
    }

    @Override
    @Transactional(readOnly = true)
    public MediaUploadResponse status(String mediaId) {

        MediaAsset captured = requireMedia(mediaId);
        missionAccess.authorizeOperator(captured.getMissionId());
        MediaUploadAttempt latest = attempts
                .findFirstByMediaIdOrderByAttemptNumberDesc(mediaId).orElse(null);

        return new MediaUploadResponse(mediaId, latest == null ? null : latest.getId(),
                latest == null ? 0 : latest.getAttemptNumber(), effectiveStatus(captured), null,
                null, Map.of(), 0, 0, null,
                manualTasks.findByMediaId(mediaId).map(ManualUploadTask::getId).orElse(null));
    }

    @Override
    @Transactional(readOnly = true)
    public MediaUploadResponse presignPart(String mediaId, String attemptId, int partNumber) {
        MediaAsset captured = requireMedia(mediaId);
        missionAccess.authorizeOperator(captured.getMissionId());
        MediaUploadAttempt attempt = requireAttempt(mediaId, attemptId);
        int count = partCount(captured.getFileSize());

        if (attempt.getStatus() != UploadAttemptStatus.PENDING
                || attempt.getMultipartUploadId() == null
                || partNumber < 1 || partNumber > count) {
            throw new ApiException(ErrorCode.MEDIA_UPLOAD_ATTEMPT_INVALID, "Invalid upload part");
        }

        var signed = storage
                .createPresignedPartUrl(attempt.getStorageKey(),
                        attempt.getMultipartUploadId(), partNumber);

        return new MediaUploadResponse(
                mediaId,
                attemptId,
                attempt.getAttemptNumber(),
                effectiveStatus(captured),
                "MULTIPART", signed.url(), signed.headers(), PART_SIZE, count,
                Instant.now().plusSeconds(signed.expiresInSeconds()), null);
    }

    @Override
    @Transactional
    public void completeMultipart(String mediaId, String attemptId,
                                  CompleteMultipartRequest request) {

        MediaAsset captured = requireMedia(mediaId);
        missionAccess.authorizeOperator(captured.getMissionId());
        MediaUploadAttempt attempt = requireAttempt(mediaId, attemptId);

        if (attempt.getStatus() == UploadAttemptStatus.UPLOADED
                || attempt.getStatus() == UploadAttemptStatus.SUCCEEDED) {
            return;
        }

        if (attempt.getStatus() != UploadAttemptStatus.PENDING
                || attempt.getMultipartUploadId() == null) {
            throw new ApiException(ErrorCode.MEDIA_UPLOAD_ATTEMPT_INVALID);
        }

        int expected = partCount(captured.getFileSize());
        List<CompleteMultipartRequest.Part> parts = request.getParts();
        if (parts.size() != expected) {
            throw new ApiException(ErrorCode.INVALID_REQUEST,
                    "Incomplete multipart upload");
        }

        Set<Integer> numbers = new HashSet<>();

        for (var part : parts) {
            if (part.getPartNumber() < 1 || part.getPartNumber() > expected
                    || !numbers.add(part.getPartNumber())) {
                throw new ApiException(ErrorCode.INVALID_REQUEST,
                        "Duplicate or invalid part number");
            }
        }

        var completed = parts
                .stream()
                .sorted(Comparator.comparingInt(CompleteMultipartRequest.Part::getPartNumber))
                .map(part -> new S3ObjectStorageService.PartETag(part.getPartNumber(),
                        part.getETag())).toList();
        // Complete may have succeeded in S3 while the previous acknowledgement was lost.
        if (objectExists(captured.getS3Bucket(), attempt.getStorageKey())) {
            markUploaded(mediaId, attemptId);
            return;
        }
        storage.completeMultipartUpload(
                attempt.getStorageKey(),
                attempt.getMultipartUploadId(),
                completed);

        markUploaded(mediaId, attemptId);
    }

    private boolean objectExists(String bucket, String key) {
        try {
            storage.inspect(bucket, key);
            return true;
        } catch (software.amazon.awssdk.services.s3.model.S3Exception exception) {
            if (exception.statusCode() == 404) return false;
            throw exception;
        }
    }

    @Override
    @Transactional
    public void markUploaded(String mediaId, String attemptId) {
        MediaAsset captured = requireMedia(mediaId);
        missionAccess.authorizeOperator(captured.getMissionId());
        MediaUploadAttempt attempt = requireAttempt(mediaId, attemptId);
        if (attempt.getStatus() == UploadAttemptStatus.SUCCEEDED
                || attempt.getStatus() == UploadAttemptStatus.UPLOADED
                || effectiveStatus(captured) == MediaStatus.AVAILABLE) {
            return;
        }
        if (attempt.getStatus() != UploadAttemptStatus.PENDING) {
            throw new ApiException(ErrorCode.MEDIA_UPLOAD_ATTEMPT_INVALID);
        }
        attempt.setStatus(UploadAttemptStatus.UPLOADED);
        attempts.save(attempt);
        captured.setMediaStatus(MediaStatus.VALIDATING);
        media.save(captured);
        audit(captured, attempt, "UPLOAD_ACKNOWLEDGED", null);
    }

    private MediaUploadResponse newAttempt(MediaAsset captured, boolean manual) {

        int number = attempts.findFirstByMediaIdOrderByAttemptNumberDesc(captured.getId())
                .map(previous -> previous.getAttemptNumber() + 1).orElse(1);
        String extension = switch (captured.getContentType()) {
            case "image/png" -> ".png";
            case "video/mp4" -> ".mp4";
            default -> ".jpg";
        };

        String prefix = s3Properties.getPrefix() == null ? ""
                : s3Properties.getPrefix().replaceAll("^/+|/+$", "") + "/";

        String key = prefix + "staging/missions/" + captured.getMissionId() + "/drones/"
                + captured.getDroneCode() + "/" + captured.getId() + "/" + number + extension;

        MediaUploadAttempt attempt = new MediaUploadAttempt();
        attempt.setMedia(captured);
        attempt.setAttemptNumber(number);
        attempt.setStatus(UploadAttemptStatus.PENDING);
        attempt.setStorageKey(key);
        attempt.setManualAttempt(manual);
        attempt.setExpiresAt(Instant.now().plusSeconds(storage.presignedUrlExpiresSeconds()));
        String method;
        String url = null;
        Map<String, List<String>> headers = Map.of();

        long partSize = 0;
        int count = 0;

        Map<String, String> metadata = Map.of(
                "media-id",
                captured.getId(),
                "sha256",
                captured.getChecksumSha256());

        if (captured.getFileSize() >= MULTIPART_THRESHOLD) {
            attempt.setMultipartUploadId(
                    storage.createMultipartUpload(key, captured.getContentType(), metadata));
            attempt.setPartSizeBytes(PART_SIZE);
            method = "MULTIPART";
            partSize = PART_SIZE;
            count = partCount(captured.getFileSize());
        } else {
            var signed = storage.createPresignedPutUrl(
                    key,
                    captured.getContentType(),
                    captured.getFileSize(),
                    metadata);
            method = "PUT";
            url = signed.url();
            headers = signed.headers();
        }
        attempts.save(attempt);
        captured.setS3Key(key);
        captured.setS3Url("s3://" + storage.bucket() + "/" + key);
        captured.setMediaStatus(MediaStatus.UPLOAD_PENDING);
        media.save(captured);

        audit(captured, attempt, manual ? "MANUAL_UPLOAD_PREPARED"
                : "UPLOAD_PREPARED", null);

        return new MediaUploadResponse(
                captured.getId(),
                attempt.getId(),
                number,
                captured.getMediaStatus(),
                method,
                url,
                headers,
                partSize,
                count,
                attempt.getExpiresAt(),
                manualTasks
                        .findByMediaId(captured.getId()).map(ManualUploadTask::getId)
                        .orElse(null));
    }

    private MediaUploadResponse existingResponse(MediaAsset captured) {

        if (captured.getMediaStatus() != MediaStatus.UPLOAD_PENDING) {
            return status(captured.getId());
        }

        MediaUploadAttempt attempt =
                attempts.findFirstByMediaIdOrderByAttemptNumberDesc(captured.getId())
                .orElseThrow(() -> new ApiException(ErrorCode.MEDIA_UPLOAD_ATTEMPT_INVALID));

        if (attempt.getMultipartUploadId() != null) {
            return new MediaUploadResponse(
                    captured.getId(),
                    attempt.getId(),
                    attempt.getAttemptNumber(),
                    captured.getMediaStatus(),
                    "MULTIPART",
                    null,
                    Map.of(), PART_SIZE,
                    partCount(captured.getFileSize()), attempt.getExpiresAt(), null);
        }

        var signed = storage.createPresignedPutUrl(
                attempt.getStorageKey(),
                captured.getContentType(),
                captured.getFileSize(),
                Map.of("media-id",
                        captured.getId(),
                        "sha256",
                        captured.getChecksumSha256()));

        attempt.setExpiresAt(Instant.now().plusSeconds(signed.expiresInSeconds()));
        attempts.save(attempt);

        return new MediaUploadResponse(
                captured.getId(),
                attempt.getId(),
                attempt.getAttemptNumber(),
                captured.getMediaStatus(),
                "PUT",
                signed.url(),
                signed.headers(),
                0,
                0,
                attempt.getExpiresAt(),
                null);
    }

    private MediaUploadResponse requireManualUpload(MediaAsset captured, String reason) {

        captured.setMediaStatus(MediaStatus.MANUAL_UPLOAD_REQUIRED);
        captured.setValidationError(reason);
        media.save(captured);
        ManualUploadTask task = manualTasks.findByMediaId(captured.getId()).orElseGet(() -> {
            ManualUploadTask created = new ManualUploadTask();
            created.setMedia(captured);
            created.setAssignedOperatorId(captured.getOperatorId());
            return created;
        });
        task.setReason(reason);
        manualTasks.save(task);
        audit(captured, null, "MANUAL_UPLOAD_REQUIRED", reason);

        return status(captured.getId());
    }

    private void validateMetadata(PrepareMediaUploadRequest request) {
        if ((request.getMediaType().equals("IMAGE") && request.getContentType().equals("video/mp4"))
                || (request.getMediaType().equals("VIDEO")
                && !request.getContentType().equals("video/mp4"))) {
            throw new ApiException(ErrorCode.INVALID_REQUEST,
                    "Media type and content type disagree");
        }
        long limit = request.getMediaType().equals("IMAGE") ? maxImageBytes : maxVideoBytes;
        if (request.getFileSize() > limit) {
            throw new ApiException(ErrorCode.INVALID_REQUEST,
                    "Media exceeds configured size limit");
        }
    }

    private Drone requireAssignedDrone(MissionMediaContext mission, String code) {
        Drone drone = drones.getEntityByCode(code);
        missionAccess.requireAssignedDrone(mission.getId(), drone.getId());
        return drone;
    }

    private MediaAsset requireMedia(String mediaId) {
        return media.findById(mediaId).orElseThrow(()
                -> new ApiException(ErrorCode.MEDIA_NOT_FOUND));
    }

    private MediaUploadAttempt requireAttempt(String mediaId, String attemptId) {
        return attempts.findByIdAndMediaId(attemptId, mediaId)
                .orElseThrow(() -> new ApiException(ErrorCode.MEDIA_UPLOAD_ATTEMPT_INVALID));
    }

    private int partCount(long size) {
        return Math.toIntExact((size + PART_SIZE - 1) / PART_SIZE);
    }

    private MediaStatus effectiveStatus(MediaAsset captured) {
        return captured.getMediaStatus() == null
                ? MediaStatus.AVAILABLE : captured.getMediaStatus();
    }

    private String actor() {
        return currentUser.getCurrentUserId();
    }

    private void audit(MediaAsset captured, MediaUploadAttempt attempt,
                       String action, String detail) {
        MediaAuditLog log = new MediaAuditLog();
        log.setMedia(captured);
        log.setAttemptId(attempt == null ? null : attempt.getId());
        log.setActorId(actor());
        log.setAction(action);
        log.setDetail(detail);
        auditLogs.save(log);
    }
}
