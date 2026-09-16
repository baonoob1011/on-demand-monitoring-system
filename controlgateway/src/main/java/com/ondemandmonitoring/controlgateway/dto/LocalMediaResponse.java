package com.ondemandmonitoring.controlgateway.dto;

public record LocalMediaResponse(
        String localMediaId,
        String missionId,
        String droneId,
        String mediaType,
        String status,
        String fileName,
        String contentType,
        long fileSize,
        String checksumSha256,
        String capturedAt,
        String backendMediaId,
        String errorCode,
        String errorMessage,
        String previewUrl
) {
}
