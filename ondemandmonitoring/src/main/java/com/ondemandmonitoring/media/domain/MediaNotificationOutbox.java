package com.ondemandmonitoring.media.domain;

import com.ondemandmonitoring.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "media_notification_outbox", uniqueConstraints =
        @UniqueConstraint(name = "uk_media_notification_type", columnNames = {"media_id", "event_type"}))
public class MediaNotificationOutbox extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "media_id", nullable = false)
    private MediaAsset media;

    @Column(name = "event_type", nullable = false, length = 60)
    private String eventType;

    @Column(name = "status", nullable = false, length = 30)
    private String status = "PENDING";
}
