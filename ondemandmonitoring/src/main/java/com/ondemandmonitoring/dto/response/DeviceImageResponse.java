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
public class DeviceImageResponse {

    String id;
    String deviceCode;
    String originalFileName;
    String contentType;
    Long fileSize;
    String s3Bucket;
    String s3Key;
    String s3Url;
    Instant createdAt;

    public static DeviceImageResponse from(DeviceImage image) {
        return DeviceImageResponse.builder()
                .id(image.getId())
                .deviceCode(image.getDeviceCode())
                .originalFileName(image.getOriginalFileName())
                .contentType(image.getContentType())
                .fileSize(image.getFileSize())
                .s3Bucket(image.getS3Bucket())
                .s3Key(image.getS3Key())
                .s3Url(image.getS3Url())
                .createdAt(image.getCreatedAt())
                .build();
    }
}
