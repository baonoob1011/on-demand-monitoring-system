package com.ondemandmonitoring.media.domain;

import com.ondemandmonitoring.common.entity.BaseEntity;
import jakarta.persistence.*;
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
@Table(name = "media_audit_logs")
public class MediaAuditLog extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "media_id", nullable = false)
    MediaAsset media;

    @Column(name = "attempt_id", length = 36)
    String attemptId;

    @Column(name = "actor_id", length = 100)
    String actorId;

    @Column(name = "action", nullable = false, length = 60)
    String action;

    @Column(name = "detail", length = 1000)
    String detail;
}
