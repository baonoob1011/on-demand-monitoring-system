package com.ondemandmonitoring.media.repository;

import com.ondemandmonitoring.media.domain.MediaUploadAttempt;
import java.util.Optional;
import java.util.List;
import com.ondemandmonitoring.media.domain.UploadAttemptStatus;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MediaUploadAttemptRepository extends JpaRepository<MediaUploadAttempt, String> {

    Optional<MediaUploadAttempt> findByIdAndMediaId(String id, String mediaId);

    Optional<MediaUploadAttempt> findFirstByMediaIdOrderByAttemptNumberDesc(String mediaId);

    Optional<MediaUploadAttempt> findByStorageKey(String storageKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from MediaUploadAttempt a where a.storageKey = :key")
    Optional<MediaUploadAttempt> findByStorageKeyForUpdate(@Param("key") String key);

    long countByMediaIdAndManualAttemptFalse(String mediaId);

    List<MediaUploadAttempt> findByStatusOrderByUpdatedAtAsc(
            UploadAttemptStatus status, Pageable pageable);
}
