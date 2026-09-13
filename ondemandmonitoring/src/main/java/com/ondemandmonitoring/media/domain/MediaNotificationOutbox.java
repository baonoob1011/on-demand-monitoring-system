package com.ondemandmonitoring.media.domain;

import com.ondemandmonitoring.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "media_notification_outbox", uniqueConstraints = @UniqueConstraint(
        name = "uk_media_notification_event", columnNames = {"media_id", "event_type"}))
public class MediaNotificationOutbox extends BaseEntity {

    @Column(name = "mission_id", nullable = false)
    private String missionId;

    @Column(name = "media_id", nullable = false)
    private String mediaId;

    @Column(name = "event_type", nullable = false, length = 80)
    private String eventType;

    @Column(name = "status", nullable = false, length = 30)
    private String status;
}
