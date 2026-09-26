package com.ondemandmonitoring.media.domain;

import com.ondemandmonitoring.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
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
@Table(name = "media_storage_event_inbox", uniqueConstraints =
        @UniqueConstraint(name = "uk_media_storage_event", columnNames = "event_key"))
public class StorageEventInbox extends BaseEntity {

    @Column(name = "event_key", nullable = false, length = 64)
    String eventKey;

    @Column(name = "bucket_name", nullable = false)
    String bucket;

    @Column(name = "object_key", nullable = false, length = 700)
    String objectKey;

    @Column(name = "processed_at", nullable = false)
    Instant processedAt;
}
