package com.ondemandmonitoring.media.repository;

import com.ondemandmonitoring.media.domain.MediaUploadAttempt;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MediaUploadAttemptRepository extends JpaRepository<MediaUploadAttempt, String> {
    Optional<MediaUploadAttempt> findByIdAndMediaId(String id, String mediaId);

    Optional<MediaUploadAttempt> findTopByMediaIdOrderByAttemptNumberDesc(String mediaId);
}
