package com.ondemandmonitoring.media.dto;

import jakarta.validation.constraints.NotBlank;

public record S3ObjectCreatedRequest(
        @NotBlank String bucket,
        @NotBlank String key,
        Long size,
        String eventId,
        String versionId) {
}
