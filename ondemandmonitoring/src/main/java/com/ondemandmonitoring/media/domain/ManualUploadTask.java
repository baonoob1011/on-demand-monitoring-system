package com.ondemandmonitoring.media.domain;

import com.ondemandmonitoring.common.entity.BaseEntity;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "manual_upload_tasks", uniqueConstraints =
        @UniqueConstraint(name = "uk_manual_upload_media", columnNames = "media_id"))
public class ManualUploadTask extends BaseEntity {
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "media_id", nullable = false)
    private MediaAsset media;

    @Column(name = "assigned_operator_id", length = 100)
    private String assignedOperatorId;

    @Column(name = "reason", length = 1000)
    private String reason;

    @Column(name = "status", nullable = false, length = 30)
    private String status = "OPEN";

    @Column(name = "resolved_at")
    private Instant resolvedAt;
}
