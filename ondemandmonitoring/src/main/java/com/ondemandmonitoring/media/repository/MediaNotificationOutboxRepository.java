package com.ondemandmonitoring.media.repository;

import com.ondemandmonitoring.media.domain.MediaNotificationOutbox;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface MediaNotificationOutboxRepository extends JpaRepository<MediaNotificationOutbox, String> {

    boolean existsByMediaIdAndEventType(String mediaId, String eventType);

    List<MediaNotificationOutbox> findByMedia_MissionIdOrderByCreatedAtDesc(String missionId);

    List<MediaNotificationOutbox> findByMedia_MissionIdInOrderByCreatedAtDesc(List<String> missionIds);
}
