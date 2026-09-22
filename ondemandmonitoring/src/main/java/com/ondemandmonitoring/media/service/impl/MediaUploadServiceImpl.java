package com.ondemandmonitoring.media.service.impl;

import com.ondemandmonitoring.common.exception.ApiException;
import com.ondemandmonitoring.common.exception.ErrorCode;
import com.ondemandmonitoring.drone.domain.Drone;
import com.ondemandmonitoring.drone.repository.DroneRepository;
import com.ondemandmonitoring.media.domain.*;
import com.ondemandmonitoring.media.dto.request.CompleteMultipartRequest;
import com.ondemandmonitoring.media.dto.request.PrepareMediaUploadRequest;
import com.ondemandmonitoring.media.dto.request.ReportUploadFailureRequest;
import com.ondemandmonitoring.media.dto.response.MediaUploadResponse;
import com.ondemandmonitoring.media.repository.*;
import com.ondemandmonitoring.media.service.IMediaUploadService;
import com.ondemandmonitoring.mission.domain.Mission;
import com.ondemandmonitoring.mission.domain.MissionDroneAssignment;
import com.ondemandmonitoring.mission.domain.MissionOperatorAssignment;
import com.ondemandmonitoring.mission.enums.MissionStatus;
import com.ondemandmonitoring.mission.repository.*;
import com.ondemandmonitoring.s3.AwsS3Properties;
import com.ondemandmonitoring.s3.S3ObjectStorageService;
import com.ondemandmonitoring.user.service.AuthenticatedUserResolver;
import java.time.Instant;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MediaUploadServiceImpl implements IMediaUploadService {
    private static final long PART_SIZE = 8L * 1024 * 1024;
    private static final long MULTIPART_THRESHOLD = 16L * 1024 * 1024;
    private static final int MAX_AUTOMATIC_ATTEMPTS = 3;
    private static final Set<MissionStatus> CAPTURE_STATUSES = Set.of(
            MissionStatus.IN_FLIGHT, MissionStatus.IN_PROGRESS, MissionStatus.RETURNING,
            MissionStatus.POSTFLIGHT_CHECKING, MissionStatus.COMPLETED);

    private final MissionRepository missions;
    private final MissionDroneAssignmentRepository droneAssignments;
    private final MissionOperatorAssignmentRepository operatorAssignments;
    private final DroneRepository drones;
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
    @Transactional
    public MediaUploadResponse prepare(String missionId, PrepareMediaUploadRequest request) {
        Mission mission = requireMission(missionId);
        authorize(mission);
        if (!CAPTURE_STATUSES.contains(mission.getStatus())) {
            throw new ApiException(ErrorCode.MEDIA_UPLOAD_NOT_ALLOWED, "Mission is not in a capture state");
        }
        Drone drone = requireAssignedDrone(mission, request.droneCode());
        validateMetadata(request);
        Optional<MediaAsset> existing = media.findByMissionIdAndDroneCodeAndLocalMediaId(
                mission.getId(), drone.getDroneCode(), request.localMediaId());
        if (existing.isPresent()) {
            MediaAsset captured = existing.get();
            if (!captured.getType().equals(request.mediaType())
                    || !captured.getContentType().equals(request.contentType())
                    || !captured.getFileSize().equals(request.fileSize())
                    || !captured.getChecksumSha256().equalsIgnoreCase(request.checksumSha256())) {
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
        captured.setLocalMediaId(request.localMediaId());
        captured.setOperatorId(actor());
        captured.setType(request.mediaType());
        captured.setStorageProvider("S3");
        captured.setOriginalFileName(request.fileName().replaceAll("[^A-Za-z0-9._-]", "_"));
        captured.setContentType(request.contentType());
        captured.setFileSize(request.fileSize());
        captured.setChecksumSha256(request.checksumSha256().toLowerCase(Locale.ROOT));
        captured.setCapturedAt(request.capturedAt());
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
        MediaAsset captured = requireMedia(mediaId);
        authorize(requireMission(captured.getMissionId()));
        if (captured.getMediaStatus() == MediaStatus.AVAILABLE) {
            return status(mediaId);
        }
        if (manual && captured.getMediaStatus() != MediaStatus.MANUAL_UPLOAD_REQUIRED) {
            throw new ApiException(ErrorCode.MEDIA_UPLOAD_ATTEMPT_INVALID, "Manual upload is not required");
        }
        if (!manual && captured.getMediaStatus() != MediaStatus.RETRY_REQUIRED) {
            throw new ApiException(ErrorCode.MEDIA_UPLOAD_ATTEMPT_INVALID, "Media is not ready for retry");
        }
        if (!manual && attempts.countByMediaIdAndManualAttemptFalse(mediaId) >= MAX_AUTOMATIC_ATTEMPTS) {
            return requireManualUpload(captured, "Automatic attempts exhausted");
        }
        return newAttempt(captured, manual);
    }

    @Override
    @Transactional
    public MediaUploadResponse reportFailure(String mediaId, String attemptId, ReportUploadFailureRequest request) {
        MediaAsset captured = requireMedia(mediaId);
        authorize(requireMission(captured.getMissionId()));
        MediaUploadAttempt attempt = requireAttempt(mediaId, attemptId);
        if (attempt.getStatus() == UploadAttemptStatus.FAILED) {
            return status(mediaId);
        }
        if (attempt.getStatus() != UploadAttemptStatus.PENDING) {
            throw new ApiException(ErrorCode.MEDIA_UPLOAD_ATTEMPT_INVALID);
        }
        attempt.setStatus(UploadAttemptStatus.FAILED);
        attempt.setFailureCode(request.code());
        attempt.setFailureMessage(request.message());
        attempt.setCompletedAt(Instant.now());
        attempts.save(attempt);
        if (attempt.getMultipartUploadId() != null) {
            try {
                storage.abortMultipartUpload(attempt.getStorageKey(), attempt.getMultipartUploadId());
            } catch (RuntimeException ignored) {
                // The failure must remain recorded; a stale S3 multipart upload can be cleaned up later.
            }
        }
        captured.setValidationError(request.message());
        audit(captured, attempt, "UPLOAD_FAILED", request.code());
        if (attempts.countByMediaIdAndManualAttemptFalse(mediaId) >= MAX_AUTOMATIC_ATTEMPTS) {
            return requireManualUpload(captured, request.message());
        }
        captured.setMediaStatus(MediaStatus.RETRY_REQUIRED);
        media.save(captured);
        return status(mediaId);
    }

    @Override
    @Transactional(readOnly = true)
    public MediaUploadResponse status(String mediaId) {
        MediaAsset captured = requireMedia(mediaId);
        authorize(requireMission(captured.getMissionId()));
        MediaUploadAttempt latest = attempts.findFirstByMediaIdOrderByAttemptNumberDesc(mediaId).orElse(null);
        return new MediaUploadResponse(mediaId, latest == null ? null : latest.getId(),
                latest == null ? 0 : latest.getAttemptNumber(), effectiveStatus(captured), null,
                null, Map.of(), 0, 0, null,
                manualTasks.findByMediaId(mediaId).map(ManualUploadTask::getId).orElse(null));
    }

    @Override
    @Transactional(readOnly = true)
    public MediaUploadResponse presignPart(String mediaId, String attemptId, int partNumber) {
        MediaAsset captured = requireMedia(mediaId);
        authorize(requireMission(captured.getMissionId()));
        MediaUploadAttempt attempt = requireAttempt(mediaId, attemptId);
        int count = partCount(captured.getFileSize());
        if (attempt.getStatus() != UploadAttemptStatus.PENDING || attempt.getMultipartUploadId() == null
                || partNumber < 1 || partNumber > count) {
            throw new ApiException(ErrorCode.MEDIA_UPLOAD_ATTEMPT_INVALID, "Invalid upload part");
        }
        var signed = storage.createPresignedPartUrl(attempt.getStorageKey(), attempt.getMultipartUploadId(), partNumber);
        return new MediaUploadResponse(mediaId, attemptId, attempt.getAttemptNumber(), effectiveStatus(captured),
                "MULTIPART", signed.url(), signed.headers(), PART_SIZE, count,
                Instant.now().plusSeconds(signed.expiresInSeconds()), null);
    }

    @Override
    @Transactional
    public void completeMultipart(String mediaId, String attemptId, CompleteMultipartRequest request) {
        MediaAsset captured = requireMedia(mediaId);
        authorize(requireMission(captured.getMissionId()));
        MediaUploadAttempt attempt = requireAttempt(mediaId, attemptId);
        if (attempt.getStatus() == UploadAttemptStatus.UPLOADED || attempt.getStatus() == UploadAttemptStatus.SUCCEEDED) {
            return;
        }
        if (attempt.getStatus() != UploadAttemptStatus.PENDING || attempt.getMultipartUploadId() == null) {
            throw new ApiException(ErrorCode.MEDIA_UPLOAD_ATTEMPT_INVALID);
        }
        int expected = partCount(captured.getFileSize());
        List<CompleteMultipartRequest.Part> parts = request.parts();
        if (parts.size() != expected) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "Incomplete multipart upload");
        }
        Set<Integer> numbers = new HashSet<>();
        for (var part : parts) {
            if (part.partNumber() < 1 || part.partNumber() > expected || !numbers.add(part.partNumber())) {
                throw new ApiException(ErrorCode.INVALID_REQUEST, "Duplicate or invalid part number");
            }
        }
        var completed = parts.stream().sorted(Comparator.comparingInt(CompleteMultipartRequest.Part::partNumber))
                .map(part -> new S3ObjectStorageService.PartETag(part.partNumber(), part.eTag())).toList();
        storage.completeMultipartUpload(attempt.getStorageKey(), attempt.getMultipartUploadId(), completed);
        markUploaded(mediaId, attemptId);
    }

    @Override
    @Transactional
    public void markUploaded(String mediaId, String attemptId) {
        MediaAsset captured = requireMedia(mediaId);
        authorize(requireMission(captured.getMissionId()));
        MediaUploadAttempt attempt = requireAttempt(mediaId, attemptId);
        if (attempt.getStatus() == UploadAttemptStatus.SUCCEEDED || attempt.getStatus() == UploadAttemptStatus.UPLOADED
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
        String prefix = s3Properties.getPrefix() == null ? "" : s3Properties.getPrefix().replaceAll("^/+|/+$", "") + "/";
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
        Map<String, String> metadata = Map.of("media-id", captured.getId(), "sha256", captured.getChecksumSha256());
        if (captured.getFileSize() >= MULTIPART_THRESHOLD) {
            attempt.setMultipartUploadId(storage.createMultipartUpload(key, captured.getContentType(), metadata));
            attempt.setPartSizeBytes(PART_SIZE);
            method = "MULTIPART";
            partSize = PART_SIZE;
            count = partCount(captured.getFileSize());
        } else {
            var signed = storage.createPresignedPutUrl(key, captured.getContentType(), captured.getFileSize(), metadata);
            method = "PUT";
            url = signed.url();
            headers = signed.headers();
        }
        attempts.save(attempt);
        captured.setS3Key(key);
        captured.setS3Url("s3://" + storage.bucket() + "/" + key);
        captured.setMediaStatus(MediaStatus.UPLOAD_PENDING);
        media.save(captured);
        audit(captured, attempt, manual ? "MANUAL_UPLOAD_PREPARED" : "UPLOAD_PREPARED", null);
        return new MediaUploadResponse(captured.getId(), attempt.getId(), number, captured.getMediaStatus(),
                method, url, headers, partSize, count, attempt.getExpiresAt(),
                manualTasks.findByMediaId(captured.getId()).map(ManualUploadTask::getId).orElse(null));
    }

    private MediaUploadResponse existingResponse(MediaAsset captured) {
        if (captured.getMediaStatus() != MediaStatus.UPLOAD_PENDING) {
            return status(captured.getId());
        }
        MediaUploadAttempt attempt = attempts.findFirstByMediaIdOrderByAttemptNumberDesc(captured.getId())
                .orElseThrow(() -> new ApiException(ErrorCode.MEDIA_UPLOAD_ATTEMPT_INVALID));
        if (attempt.getMultipartUploadId() != null) {
            return new MediaUploadResponse(captured.getId(), attempt.getId(), attempt.getAttemptNumber(),
                    captured.getMediaStatus(), "MULTIPART", null, Map.of(), PART_SIZE,
                    partCount(captured.getFileSize()), attempt.getExpiresAt(), null);
        }
        var signed = storage.createPresignedPutUrl(attempt.getStorageKey(), captured.getContentType(),
                captured.getFileSize(), Map.of("media-id", captured.getId(), "sha256", captured.getChecksumSha256()));
        attempt.setExpiresAt(Instant.now().plusSeconds(signed.expiresInSeconds()));
        attempts.save(attempt);
        return new MediaUploadResponse(captured.getId(), attempt.getId(), attempt.getAttemptNumber(),
                captured.getMediaStatus(), "PUT", signed.url(), signed.headers(), 0, 0, attempt.getExpiresAt(), null);
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
        if ((request.mediaType().equals("IMAGE") && request.contentType().equals("video/mp4"))
                || (request.mediaType().equals("VIDEO") && !request.contentType().equals("video/mp4"))) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "Media type and content type disagree");
        }
        long limit = request.mediaType().equals("IMAGE") ? maxImageBytes : maxVideoBytes;
        if (request.fileSize() > limit) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "Media exceeds configured size limit");
        }
    }

    private Mission requireMission(String identifier) {
        return missions.findById(identifier).or(() -> missions.findByMissionCode(identifier))
                .orElseThrow(() -> new ApiException(ErrorCode.MISSION_NOT_FOUND));
    }

    private Drone requireAssignedDrone(Mission mission, String code) {
        Drone drone = drones.findByDroneCode(code)
                .orElseThrow(() -> new ApiException(ErrorCode.DRONE_NOT_FOUND));
        boolean assigned = droneAssignments.findByMissionIdAndIsCurrentTrue(mission.getId())
                .map(MissionDroneAssignment::getDrone).map(Drone::getId)
                .filter(drone.getId()::equals).isPresent();
        if (!assigned && mission.getStatus() == MissionStatus.COMPLETED) {
            assigned = droneAssignments.findByMissionId(mission.getId()).stream()
                    .anyMatch(entry -> entry.getDrone().getId().equals(drone.getId())
                            && "MISSION_COMPLETE".equals(entry.getReleaseReason()));
        }
        if (!assigned) {
            throw new ApiException(ErrorCode.ACCESS_DENIED, "Drone is not assigned to mission");
        }
        return drone;
    }

    private void authorize(Mission mission) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new ApiException(ErrorCode.UNAUTHORIZED);
        }
        boolean privileged = auth.getAuthorities().stream().anyMatch(authority ->
                authority.getAuthority().equals("ROLE_ADMIN")
                        || authority.getAuthority().equals("ROLE_SYSTEM_OPERATOR"));
        String userId = currentUser.getCurrentUser().getId().toString();
        boolean assigned = operatorAssignments.findByMissionIdAndIsCurrentTrue(mission.getId())
                .map(MissionOperatorAssignment::getOperatorId).filter(userId::equals).isPresent();
        if (!assigned && mission.getStatus() == MissionStatus.COMPLETED) {
            assigned = operatorAssignments.findByMissionId(mission.getId()).stream()
                    .anyMatch(entry -> userId.equals(entry.getOperatorId())
                            && "COMPLETED".equals(entry.getStatus()));
        }
        if (!privileged && !assigned) {
            throw new ApiException(ErrorCode.ACCESS_DENIED, "Operator is not assigned to mission");
        }
    }

    private MediaAsset requireMedia(String mediaId) {
        return media.findById(mediaId).orElseThrow(() -> new ApiException(ErrorCode.MEDIA_NOT_FOUND));
    }

    private MediaUploadAttempt requireAttempt(String mediaId, String attemptId) {
        return attempts.findByIdAndMediaId(attemptId, mediaId)
                .orElseThrow(() -> new ApiException(ErrorCode.MEDIA_UPLOAD_ATTEMPT_INVALID));
    }

    private int partCount(long size) {
        return Math.toIntExact((size + PART_SIZE - 1) / PART_SIZE);
    }

    private MediaStatus effectiveStatus(MediaAsset captured) {
        return captured.getMediaStatus() == null ? MediaStatus.AVAILABLE : captured.getMediaStatus();
    }

    private String actor() {
        return currentUser.getCurrentUser().getId().toString();
    }

    private void audit(MediaAsset captured, MediaUploadAttempt attempt, String action, String detail) {
        MediaAuditLog log = new MediaAuditLog();
        log.setMedia(captured);
        log.setAttemptId(attempt == null ? null : attempt.getId());
        log.setActorId(actor());
        log.setAction(action);
        log.setDetail(detail);
        auditLogs.save(log);
    }
}
