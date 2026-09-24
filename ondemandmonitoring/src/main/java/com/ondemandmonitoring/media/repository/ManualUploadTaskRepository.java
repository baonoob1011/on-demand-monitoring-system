package com.ondemandmonitoring.media.repository;

import com.ondemandmonitoring.media.domain.ManualUploadTask;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ManualUploadTaskRepository extends JpaRepository<ManualUploadTask, String> {

    Optional<ManualUploadTask> findByMediaId(String mediaId);
}
