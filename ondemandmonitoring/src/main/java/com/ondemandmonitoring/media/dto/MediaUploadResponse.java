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
        String uploadUrl,
        Map<String, List<String>> requiredHeaders,
        Instant expiresAt,
        String manualUploadTaskId) {
}
