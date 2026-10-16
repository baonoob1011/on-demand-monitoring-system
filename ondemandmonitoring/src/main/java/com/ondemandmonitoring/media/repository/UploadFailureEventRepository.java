package com.ondemandmonitoring.media.repository;

import com.ondemandmonitoring.media.domain.UploadFailureEvent;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface UploadFailureEventRepository extends JpaRepository<UploadFailureEvent, String> {

    Optional<UploadFailureEvent> findByEventKey(String eventKey);

    /** Row lock so concurrent replay of one event key serialises on the database. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from UploadFailureEvent e where e.eventKey = :key")
    Optional<UploadFailureEvent> findByEventKeyForUpdate(@Param("key") String key);

    List<UploadFailureEvent> findByProcessedAtIsNullOrderByOccurredAtAsc();
}