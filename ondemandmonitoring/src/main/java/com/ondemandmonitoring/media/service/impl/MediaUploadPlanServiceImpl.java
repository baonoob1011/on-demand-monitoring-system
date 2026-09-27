package com.ondemandmonitoring.media.service.impl;

import com.ondemandmonitoring.common.exception.ApiException;
import com.ondemandmonitoring.common.exception.ErrorCode;
import com.ondemandmonitoring.media.domain.MediaAsset;
import com.ondemandmonitoring.media.domain.MediaUploadAttempt;
import com.ondemandmonitoring.media.domain.MediaStatus;
import com.ondemandmonitoring.media.domain.UploadAttemptStatus;
import com.ondemandmonitoring.media.domain.ManualUploadTask;
import com.ondemandmonitoring.media.dto.response.MediaUploadResponse;
import com.ondemandmonitoring.media.enums.MediaAuditAction;
import com.ondemandmonitoring.media.mapper.MediaWorkflowMapper;
import com.ondemandmonitoring.media.policy.MediaUploadPolicy;
import com.ondemandmonitoring.media.repository.MediaAssetRepository;
import com.ondemandmonitoring.media.repository.MediaUploadAttemptRepository;
import com.ondemandmonitoring.media.repository.ManualUploadTaskRepository;
import com.ondemandmonitoring.media.service.IMediaAuditService;
import com.ondemandmonitoring.media.service.IMediaUploadPlanService;
import com.ondemandmonitoring.s3.S3ObjectStorageService;
import com.ondemandmonitoring.s3.AwsS3Properties;
import com.ondemandmonitoring.user.service.AuthenticatedUserResolver;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class MediaUploadPlanServiceImpl implements IMediaUploadPlanService {
    MediaAssetRepository media;
    MediaUploadAttemptRepository attempts;
    ManualUploadTaskRepository manualTasks;
    IMediaAuditService auditService;
    MediaWorkflowMapper mapper;
    MediaUploadPolicy policy;
    S3ObjectStorageService storage;
    AwsS3Properties s3Properties;
    AuthenticatedUserResolver currentUser;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public MediaUploadResponse create(MediaAsset captured, boolean manual) {

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

        if (policy.useMultipart(captured.getFileSize())) {
            attempt.setMultipartUploadId(
                    storage.createMultipartUpload(key, captured.getContentType(), metadata));
            attempt.setPartSizeBytes(MediaUploadPolicy.PART_SIZE_BYTES);
            method = "MULTIPART";
            partSize = MediaUploadPolicy.PART_SIZE_BYTES;
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

        audit(captured, attempt, manual ? MediaAuditAction.MANUAL_UPLOAD_PREPARED
                : MediaAuditAction.UPLOAD_PREPARED, null);

        return mapper.toUploadResponse(captured, attempt,
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

    public MediaUploadResponse resume(MediaAsset captured) {

        if (captured.getMediaStatus() != MediaStatus.UPLOAD_PENDING) {
            throw new ApiException(ErrorCode.MEDIA_UPLOAD_ATTEMPT_INVALID);
        }

        MediaUploadAttempt attempt =
                attempts.findFirstByMediaIdOrderByAttemptNumberDesc(captured.getId())
                .orElseThrow(() -> new ApiException(ErrorCode.MEDIA_UPLOAD_ATTEMPT_INVALID));

        if (attempt.getMultipartUploadId() != null) {
            return mapper.toUploadResponse(captured, attempt,
                "MULTIPART",
                null,
                Map.of(),
                MediaUploadPolicy.PART_SIZE_BYTES,
                partCount(captured.getFileSize()),
                attempt.getExpiresAt(),
                null);
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

        return mapper.toUploadResponse(captured, attempt,
                "PUT",
                signed.url(),
                signed.headers(),
                0,
                0,
                attempt.getExpiresAt(),
                null);
    }

    private int partCount(long size) {
        return policy.partCount(size);
    }

    private void audit(MediaAsset asset, MediaUploadAttempt attempt, MediaAuditAction action, String detail) {
        auditService.record(asset, attempt, currentUser.getCurrentUserId(), action, detail);
    }
}

