package com.ondemandmonitoring.media.dto.response;

import java.time.Instant;

public record CustomerMediaResponse(String mediaId, String missionId, String droneCode,
                                    String mediaType, String fileName, String contentType,
                                    long fileSize, Instant capturedAt, Instant availableAt,
                                    String downloadUrl) {
}
