package com.ondemandmonitoring.media.dto;

import com.ondemandmonitoring.media.domain.MediaStatus;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public record MediaUploadResponse(
        String mediaId,
        String attemptId,
        int attemptNumber,
        MediaStatus status,
        String uploadMethod,
        String uploadUrl,
        Map<String, List<String>> uploadHeaders,
        long partSizeBytes,
        int partCount,
        Instant expiresAt,
        String manualTaskId) {
}
