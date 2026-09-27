package com.ondemandmonitoring.media.service.impl;

import com.ondemandmonitoring.media.domain.MediaAsset;
import com.ondemandmonitoring.media.domain.MediaAuditLog;
import com.ondemandmonitoring.media.domain.MediaUploadAttempt;
import com.ondemandmonitoring.media.enums.MediaAuditAction;
import com.ondemandmonitoring.media.repository.MediaAuditLogRepository;
import com.ondemandmonitoring.media.service.IMediaAuditService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class MediaAuditServiceImpl implements IMediaAuditService {
    MediaAuditLogRepository auditLogs;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(MediaAsset asset, MediaUploadAttempt attempt, String actorId, MediaAuditAction action, String detail) {
        MediaAuditLog entry = new MediaAuditLog();
        entry.setMedia(asset);
        entry.setAttemptId(attempt == null ? null : attempt.getId());
        entry.setActorId(actorId);
        entry.setAction(action.name());
        entry.setDetail(detail);
        auditLogs.save(entry);
    }
}
