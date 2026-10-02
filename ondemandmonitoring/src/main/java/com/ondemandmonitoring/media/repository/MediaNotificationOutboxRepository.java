package com.ondemandmonitoring.media.repository;

import com.ondemandmonitoring.media.domain.MediaNotificationOutbox;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface MediaNotificationOutboxRepository extends JpaRepository<MediaNotificationOutbox, String> {

    boolean existsByMediaIdAndEventType(String mediaId, String eventType);

    @Query("select n from MediaNotificationOutbox n where n.missionId = :missionId order by n.createdAt desc")
    List<MediaNotificationOutbox> findByMedia_MissionIdOrderByCreatedAtDesc(@Param("missionId") String missionId);

    @Query("select n from MediaNotificationOutbox n where n.missionId in :missionIds order by n.createdAt desc")
    List<MediaNotificationOutbox> findByMedia_MissionIdInOrderByCreatedAtDesc(
            @Param("missionIds") List<String> missionIds);
}
