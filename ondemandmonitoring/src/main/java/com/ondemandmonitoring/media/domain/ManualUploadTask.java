package com.ondemandmonitoring.media.domain;

import com.ondemandmonitoring.common.entity.BaseEntity;
import com.ondemandmonitoring.device.domain.DeviceImage;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "manual_upload_tasks")
public class ManualUploadTask extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "media_id", nullable = false, unique = true)
    private DeviceImage media;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private ManualUploadTaskStatus status;

    @Column(name = "assigned_operator_id", length = 100)
    private String assignedOperatorId;

    @Column(name = "reason", nullable = false, length = 1000)
    private String reason;

    @Column(name = "resolved_at")
    private Instant resolvedAt;
}
