package com.ondemandmonitoring.media.service;

import com.ondemandmonitoring.media.domain.MediaAsset;
import com.ondemandmonitoring.media.domain.MediaUploadAttempt;
import com.ondemandmonitoring.media.enums.MediaAuditAction;

public interface IMediaAuditService {
    void record(MediaAsset asset, MediaUploadAttempt attempt, String actorId, MediaAuditAction action, String detail);
}
