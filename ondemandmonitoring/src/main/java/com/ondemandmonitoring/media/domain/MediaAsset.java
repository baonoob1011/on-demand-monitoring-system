package com.ondemandmonitoring.media.domain;

import com.ondemandmonitoring.common.entity.BaseEntity;
import com.ondemandmonitoring.drone.domain.Drone;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.time.Instant;
import com.ondemandmonitoring.media.domain.MediaStatus;

@Getter
@Setter
@Entity
@Table(name = "drone_media")
@FieldDefaults(level = AccessLevel.PRIVATE)
public class MediaAsset extends BaseEntity {

    @Column(name = "drone_code", nullable = false, length = 50)
    String droneCode;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "drone_id", nullable = false)
    Drone drone;

    @Column(name = "mission_id", nullable = false, length = 100)
    String missionId;

    @Column(name = "media_type", nullable = false, length = 50)
    String type;

    @Column(name = "storage_provider", nullable = false, length = 50)
    String storageProvider;

    @Column(name = "original_file_name", nullable = false)
    String originalFileName;

    @Column(name = "content_type", nullable = false, length = 100)
    String contentType;

    @Column(name = "file_size", nullable = false)
    Long fileSize;

    @Column(name = "s3_bucket", nullable = false)
    String s3Bucket;

    @Column(name = "s3_key", nullable = false, unique = true)
    String s3Key;

    @Column(name = "s3_url", nullable = false, length = 1000)
    String s3Url;

    @Column(name = "captured_at", nullable = false)
    Instant capturedAt;

    @Column(name = "local_media_id", length = 100)
    String localMediaId;

    @Enumerated(EnumType.STRING)
    @Column(name = "media_status", length = 40)
    MediaStatus mediaStatus = MediaStatus.AVAILABLE;

    @Column(name = "checksum_sha256", length = 64)
    String checksumSha256;

    @Column(name = "upload_attempt_count")
    Integer uploadAttemptCount = 0;

    @Column(name = "validated_at")
    Instant validatedAt;

    @Column(name = "available_at")
    Instant availableAt;

    @Column(name = "validation_error", length = 1000)
    String validationError;
}
