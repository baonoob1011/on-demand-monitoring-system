package com.ondemandmonitoring.media.event;

import java.time.Instant;

public record StorageObjectCreatedEvent(
        String bucket,
        String key,
        Long size,
        String eventId,
        String versionId,
        String sequencer,
        String eventName,
        Instant eventTime) {
}
