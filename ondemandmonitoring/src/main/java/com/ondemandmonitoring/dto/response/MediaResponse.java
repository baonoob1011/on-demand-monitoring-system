package com.ondemandmonitoring.dto.response;

import com.ondemandmonitoring.entities.DeviceImage;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

@Getter
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class MediaResponse {

    String id;
    String missionId;
    String droneId;
    String type;
    String url;
    Long expiresIn;
    String contentType;
    Long fileSize;
    Instant capturedAt;

    public static MediaResponse from(DeviceImage image, String presignedUrl, long expiresInSeconds) {
        return MediaResponse.builder()
                .id(image.getId())
                .missionId(image.getMissionId())
                .droneId(image.getDeviceCode())
                .type(image.getType())
                .url(presignedUrl)
                .expiresIn(expiresInSeconds)
                .contentType(image.getContentType())
                .fileSize(image.getFileSize())
                .capturedAt(image.getCapturedAt())
                .build();
    }
}
