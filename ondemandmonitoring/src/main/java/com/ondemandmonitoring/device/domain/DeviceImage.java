package com.ondemandmonitoring.device.domain;

import com.ondemandmonitoring.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "device_images")
@FieldDefaults(level = AccessLevel.PRIVATE)
public class DeviceImage extends BaseEntity {

    @Column(name = "device_code", nullable = false, length = 50)
    String deviceCode;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "device_id", nullable = false)
    Device device;

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
}
