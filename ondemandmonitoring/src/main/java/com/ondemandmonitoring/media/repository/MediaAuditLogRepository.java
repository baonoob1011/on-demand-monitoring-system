package com.ondemandmonitoring.media.repository;

import com.ondemandmonitoring.media.domain.MediaAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MediaAuditLogRepository extends JpaRepository<MediaAuditLog, String> {
}
