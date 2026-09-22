package com.ondemandmonitoring.media.domain;

import com.ondemandmonitoring.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "media_audit_logs")
public class MediaAuditLog extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "media_id", nullable = false)
    private MediaAsset media;

    @Column(name = "attempt_id", length = 36)
    private String attemptId;

    @Column(name = "actor_id", length = 100)
    private String actorId;

    @Column(name = "action", nullable = false, length = 60)
    private String action;

    @Column(name = "detail", length = 1000)
    private String detail;
}
