package com.ondemandmonitoring.media.domain;

import com.ondemandmonitoring.common.entity.BaseEntity;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "media_upload_attempts", uniqueConstraints = {
        @UniqueConstraint(name = "uk_media_attempt_number", columnNames = {"media_id", "attempt_number"}),
        @UniqueConstraint(name = "uk_media_attempt_storage_key", columnNames = "storage_key")
})
public class MediaUploadAttempt extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "media_id", nullable = false)
    private MediaAsset media;

    @Column(name = "attempt_number", nullable = false)
    private int attemptNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private UploadAttemptStatus status;

    @Column(name = "storage_key", nullable = false, length = 700)
    private String storageKey;

    @Column(name = "multipart_upload_id", length = 500)
    private String multipartUploadId;

    @Column(name = "part_size_bytes")
    private Long partSizeBytes;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "failure_code", length = 100)
    private String failureCode;

    @Column(name = "failure_message", length = 1000)
    private String failureMessage;

    @Column(name = "manual_attempt", nullable = false)
    private boolean manualAttempt;
}
