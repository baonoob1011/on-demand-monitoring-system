package com.ondemandmonitoring.media.repository;

import com.ondemandmonitoring.media.domain.StorageEventInbox;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StorageEventInboxRepository extends JpaRepository<StorageEventInbox, String> {
    boolean existsByEventKey(String eventKey);
}
