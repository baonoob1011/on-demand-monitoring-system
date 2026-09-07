package com.ondemandmonitoring.device.dto.response;

import com.ondemandmonitoring.device.domain.DeviceImage;
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
}

