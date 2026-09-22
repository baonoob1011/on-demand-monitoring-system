package com.ondemandmonitoring.media.repository;

import com.ondemandmonitoring.media.domain.MediaNotificationOutbox;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MediaNotificationOutboxRepository extends JpaRepository<MediaNotificationOutbox, String> {
    boolean existsByMediaIdAndEventType(String mediaId, String eventType);
}
