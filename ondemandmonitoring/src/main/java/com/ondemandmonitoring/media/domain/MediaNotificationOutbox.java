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
@Table(name = "media_notification_outbox", uniqueConstraints =
        @UniqueConstraint(name = "uk_media_notification_type", columnNames = {"media_id", "event_type"}))
public class MediaNotificationOutbox extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "media_id", nullable = false)
    MediaAsset media;

    @Column(name = "mission_id", nullable = false, length = 255)
    String missionId;

    @Column(name = "event_type", nullable = false, length = 60)
    String eventType;

    @Column(name = "status", nullable = false, length = 30)
    @Builder.Default
    String status = "PENDING";
}
