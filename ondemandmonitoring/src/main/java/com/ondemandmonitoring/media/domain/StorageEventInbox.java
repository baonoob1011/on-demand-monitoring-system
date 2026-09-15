package com.ondemandmonitoring.media.domain;

import com.ondemandmonitoring.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "media_storage_event_inbox", uniqueConstraints = @UniqueConstraint(
        name = "uk_media_storage_event_key", columnNames = "event_key"))
public class StorageEventInbox extends BaseEntity {

    @Column(name = "event_key", nullable = false, length = 64)
    private String eventKey;

    @Column(name = "bucket_name", nullable = false)
    private String bucket;

    @Column(name = "object_key", nullable = false, length = 1024)
    private String objectKey;

    @Column(name = "event_name", length = 100)
    private String eventName;

    @Column(name = "source_event_id", length = 255)
    private String sourceEventId;

    @Column(name = "object_version_id", length = 255)
    private String objectVersionId;

    @Column(name = "sequencer", length = 255)
    private String sequencer;

    @Column(name = "source_event_time")
    private Instant sourceEventTime;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;
}
