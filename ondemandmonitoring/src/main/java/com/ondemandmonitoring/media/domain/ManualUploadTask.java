package com.ondemandmonitoring.media.domain;

import com.ondemandmonitoring.common.entity.BaseEntity;
import jakarta.persistence.*;
import java.time.Instant;
import com.ondemandmonitoring.media.enums.ManualUploadTaskStatus;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import lombok.Getter;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@FieldDefaults(level = AccessLevel.PRIVATE)
@Table(name = "manual_upload_tasks", uniqueConstraints =
        @UniqueConstraint(name = "uk_manual_upload_media", columnNames = "media_id"))
public class ManualUploadTask extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "media_id", nullable = false)
    MediaAsset media;

    @Column(name = "assigned_operator_id", length = 100)
    String assignedOperatorId;

    @Column(name = "reason", length = 1000)
    String reason;

    @Column(name = "status", nullable = false, length = 30)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    ManualUploadTaskStatus status = ManualUploadTaskStatus.OPEN;

    @Column(name = "resolved_at")
    Instant resolvedAt;
}
