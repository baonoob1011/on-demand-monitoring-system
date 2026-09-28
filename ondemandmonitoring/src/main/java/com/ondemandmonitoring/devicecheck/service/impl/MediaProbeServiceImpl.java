package com.ondemandmonitoring.devicecheck.service.impl;

import com.ondemandmonitoring.common.exception.ApiException;
import com.ondemandmonitoring.common.exception.ErrorCode;
import com.ondemandmonitoring.devicecheck.domain.PersistedPreDeviceCheck;
import com.ondemandmonitoring.devicecheck.dto.request.MediaProbeRequest;
import com.ondemandmonitoring.devicecheck.dto.request.PreDeviceCheckItemUpdateRequest;
import com.ondemandmonitoring.devicecheck.dto.response.MediaProbeResponse;
import com.ondemandmonitoring.devicecheck.enums.PreDeviceCheckType;
import com.ondemandmonitoring.devicecheck.enums.PreDeviceItemStatus;
import com.ondemandmonitoring.devicecheck.repository.PersistedPreDeviceCheckRepository;
import com.ondemandmonitoring.devicecheck.service.IMediaProbeService;
import com.ondemandmonitoring.devicecheck.service.IPersistedPreDeviceCheckService;
import com.ondemandmonitoring.mission.dto.response.MissionResponse;
import com.ondemandmonitoring.mission.service.IMissionService;
import com.ondemandmonitoring.s3.S3ObjectStorageService;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class MediaProbeServiceImpl implements IMediaProbeService {

    private static final String JPEG_CONTENT_TYPE = "image/jpeg";

    private final PersistedPreDeviceCheckRepository runRepository;
    private final IPersistedPreDeviceCheckService preDeviceChecks;
    private final IMissionService missions;
    private final S3ObjectStorageService storage;

    @Value("${app.device-check.media-probe.max-bytes:262144}")
    private long maxBytes;

    @Value("${app.device-check.media-probe.prefix:diagnostics/media-probes}")
    private String probePrefix;

    @Override
    public MediaProbeResponse verify(String runId, MediaProbeRequest request) {
        PersistedPreDeviceCheck run = runRepository.findById(runId)
                .orElseThrow(() -> new ApiException(
                        ErrorCode.RESOURCE_NOT_FOUND,
                        "Pre-device check not found: " + runId));
        String missionId = run.getMission().getId();
        MissionResponse mission = missions.getByIdResponse(missionId);
        String deviceId = mission.getDeviceId();
        if (deviceId == null || deviceId.isBlank()) {
            return fail(runId, "Mission has no assigned device");
        }

        byte[] uploaded;
        try {
            uploaded = readAndValidate(request.getFile());
        } catch (ApiException exception) {
            recordOutcome(runId, PreDeviceItemStatus.FAILED, exception.getMessage());
            throw exception;
        }
        String uploadedChecksum = sha256(uploaded);
        if (!uploadedChecksum.equalsIgnoreCase(request.getChecksumSha256())) {
            return fail(runId, "Probe checksum does not match uploaded bytes");
        }

        String bucket = storage.bucket();
        if (bucket == null || bucket.isBlank()) {
            return fail(runId, "S3 bucket is not configured");
        }

        String key = normalizedPrefix() + "/" + runId + "/" + UUID.randomUUID() + ".jpg";
        RuntimeException failure = null;
        boolean stored = false;
        boolean cleanupVerified = false;
        try {
            storage.put(key, JPEG_CONTENT_TYPE, uploaded.length,
                    new ByteArrayInputStream(uploaded), "[MEDIA-PROBE]");
            stored = true;
            S3ObjectStorageService.StoredObjectStream downloaded = storage.open(bucket, key);
            try (InputStream input = downloaded.inputStream()) {
                byte[] actual = input.readAllBytes();
                if (!JPEG_CONTENT_TYPE.equalsIgnoreCase(downloaded.contentType())) {
                    throw probeFailure("Stored probe has unexpected content type");
                }
                if (actual.length != uploaded.length || !uploadedChecksum.equals(sha256(actual))) {
                    throw probeFailure("Stored probe content does not match uploaded bytes");
                }
            }
        } catch (IOException exception) {
            failure = probeFailure("Cannot read the stored media probe", exception);
        } catch (RuntimeException exception) {
            failure = exception instanceof ApiException
                    ? exception
                    : probeFailure("Media probe storage operation failed", exception);
        } finally {
            if (stored) {
                try {
                    storage.delete(bucket, key);
                    cleanupVerified = true;
                } catch (RuntimeException cleanupException) {
                    if (failure == null) {
                        failure = probeFailure("Media probe cleanup failed", cleanupException);
                    }
                }
            }
        }

        if (failure != null) {
            recordOutcome(runId, PreDeviceItemStatus.FAILED, failure.getMessage());
            throw failure;
        }

        String message = "JPEG storage round-trip verified; checksum and cleanup passed";
        recordOutcome(runId, PreDeviceItemStatus.PASSED, message);
        return MediaProbeResponse.builder()
                .status(PreDeviceItemStatus.PASSED.name())
                .runId(runId)
                .missionId(missionId)
                .deviceId(deviceId)
                .contentType(JPEG_CONTENT_TYPE)
                .sizeBytes(uploaded.length)
                .checksumVerified(true)
                .storageVerified(true)
                .cleanupVerified(cleanupVerified)
                .message(message)
                .checkedAt(Instant.now())
                .build();
    }

    private byte[] readAndValidate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw invalidProbe("Probe JPEG is required");
        }
        if (file.getSize() > maxBytes) {
            throw invalidProbe("Probe JPEG exceeds maximum size of " + maxBytes + " bytes");
        }
        if (!JPEG_CONTENT_TYPE.equalsIgnoreCase(file.getContentType())) {
            throw invalidProbe("Probe content type must be image/jpeg");
        }
        try {
            byte[] bytes = file.getBytes();
            if (bytes.length < 4
                    || (bytes[0] & 0xff) != 0xff || (bytes[1] & 0xff) != 0xd8
                    || (bytes[bytes.length - 2] & 0xff) != 0xff
                    || (bytes[bytes.length - 1] & 0xff) != 0xd9) {
                throw invalidProbe("Probe file is not a valid JPEG payload");
            }
            return bytes;
        } catch (IOException exception) {
            throw invalidProbe("Cannot read probe JPEG", exception);
        }
    }

    private MediaProbeResponse fail(String runId, String message) {
        recordOutcome(runId, PreDeviceItemStatus.FAILED, message);
        throw probeFailure(message);
    }

    private void recordOutcome(String runId, PreDeviceItemStatus status, String message) {
        PreDeviceCheckItemUpdateRequest update = new PreDeviceCheckItemUpdateRequest();
        update.setStatus(status);
        update.setMessage(message);
        preDeviceChecks.update(runId, PreDeviceCheckType.MEDIA.code(), update);
    }

    private String normalizedPrefix() {
        String normalized = probePrefix == null ? "" : probePrefix.strip().replaceAll("^/+|/+$", "");
        return normalized.isBlank() ? "diagnostics/media-probes" : normalized;
    }

    private String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private ApiException invalidProbe(String message) {
        return new ApiException(ErrorCode.MEDIA_PROBE_INVALID, message);
    }

    private ApiException invalidProbe(String message, Throwable cause) {
        ApiException exception = invalidProbe(message);
        exception.initCause(cause);
        return exception;
    }

    private ApiException probeFailure(String message) {
        return new ApiException(ErrorCode.MEDIA_PROBE_FAILED, message);
    }

    private ApiException probeFailure(String message, Throwable cause) {
        ApiException exception = probeFailure(message);
        exception.initCause(cause);
        return exception;
    }
}
