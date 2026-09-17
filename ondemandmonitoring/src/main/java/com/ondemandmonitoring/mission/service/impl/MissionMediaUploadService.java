package com.ondemandmonitoring.mission.service.impl;

import com.ondemandmonitoring.common.exception.ApiException;
import com.ondemandmonitoring.common.exception.ErrorCode;
import com.ondemandmonitoring.media.domain.MediaAsset;
import com.ondemandmonitoring.media.service.IMediaAssetService;
import com.ondemandmonitoring.mission.service.IMissionMediaUploadService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;

/**
 * Implementation of {@link IMissionMediaUploadService} with 3-retry upload logic.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class MissionMediaUploadService implements IMissionMediaUploadService {

    static final int MAX_ATTEMPTS = 3;
    static final long RETRY_DELAY_MS = 2_000L;

    IMediaAssetService mediaAssetService;

    @Override
    public MediaAsset uploadWithRetry(String missionId, String droneCode, MultipartFile file) {
        return uploadWithRetry(missionId, droneCode, file, null);
    }

    @Override
    public MediaAsset uploadWithRetry(String missionId, String droneCode, MultipartFile file, String mediaType) {
        Exception lastException = null;

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                log.info("Mission {} – media upload attempt {}/{}", missionId, attempt, MAX_ATTEMPTS);
                MediaAsset image = mediaAssetService.upload(missionId, droneCode, Instant.now(), file, mediaType);
                log.info("Mission {} – media uploaded successfully on attempt {}", missionId, attempt);
                return image;
            } catch (Exception ex) {
                lastException = ex;
                log.warn("Mission {} – upload attempt {}/{} failed: {}", missionId, attempt, MAX_ATTEMPTS, ex.getMessage());
                if (attempt < MAX_ATTEMPTS) {
                    sleepQuietly(RETRY_DELAY_MS);
                }
            }
        }

        createManualUploadNotification(missionId, droneCode);
        throw new ApiException(ErrorCode.MEDIA_UPLOAD_FAILED,
                "Upload thất bại sau " + MAX_ATTEMPTS + " lần thử cho mission " + missionId
                        + ". Nguyên nhân: " + (lastException != null ? lastException.getMessage() : "unknown"));
    }

    protected void createManualUploadNotification(String missionId, String droneCode) {
        log.error("[MANUAL UPLOAD REQUIRED] Mission {} – drone {} – upload failed after {} attempts. "
                + "Operator must upload media manually.", missionId, droneCode, MAX_ATTEMPTS);
    }

    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }
}
