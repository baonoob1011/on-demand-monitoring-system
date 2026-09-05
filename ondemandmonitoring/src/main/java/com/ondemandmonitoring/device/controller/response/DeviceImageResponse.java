package com.ondemandmonitoring.device.controller.response;

import com.ondemandmonitoring.device.domain.media.DeviceImage;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

@Getter
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class DeviceImageResponse {

    String id;
    String deviceCode;
    String missionId;
    String type;
    String storageProvider;
    String originalFileName;
    String contentType;
    Long fileSize;
    String s3Bucket;
    String s3Key;
    String s3Url;
    Instant capturedAt;
    Instant createdAt;

    public static DeviceImageResponse from(DeviceImage image) {
        return DeviceImageResponse.builder()
                .id(image.getId())
                .deviceCode(image.getDeviceCode())
                .missionId(image.getMissionId())
                .type(image.getType())
                .storageProvider(image.getStorageProvider())
                .originalFileName(image.getOriginalFileName())
                .contentType(image.getContentType())
                .fileSize(image.getFileSize())
                .s3Bucket(image.getS3Bucket())
                .s3Key(image.getS3Key())
                .s3Url(image.getS3Url())
                .capturedAt(image.getCapturedAt())
                .createdAt(image.getCreatedAt())
                .build();
    }
}
