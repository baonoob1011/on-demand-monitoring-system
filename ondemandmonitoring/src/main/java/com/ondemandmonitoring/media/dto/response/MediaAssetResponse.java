package com.ondemandmonitoring.media.dto.response;

import java.time.Instant;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

@Getter
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class MediaAssetResponse {

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
