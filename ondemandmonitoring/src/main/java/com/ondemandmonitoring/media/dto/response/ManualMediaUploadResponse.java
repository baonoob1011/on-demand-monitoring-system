package com.ondemandmonitoring.media.dto.response;

import java.time.Instant;

public record ManualMediaUploadResponse(
        String manualTaskId, String backendMediaId, String localMediaId,
        String missionId, String droneCode, String mediaType, String fileName,
        String contentType, Long fileSize, String checksumSha256,
        Instant capturedAt, String status, String reason) {
}
