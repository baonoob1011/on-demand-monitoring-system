package com.ondemandmonitoring.mission.service.impl;

import com.ondemandmonitoring.media.domain.MediaAsset;
import com.ondemandmonitoring.media.domain.MediaStatus;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Immutable policy V1: provenance is never inferred from filename, storage key or upload method. */
@Component
public class ChecklistEvidencePolicy {
    public String ineligibilityReason(MediaAsset media, boolean finalApproval) {
        if (!"DRONE_CAMERA".equals(media.getSourceType())) return "EVIDENCE_SOURCE_NOT_ELIGIBLE";
        if (!Set.of("IMAGE", "VIDEO").contains(media.getType() == null ? "" : media.getType())) return "EVIDENCE_TYPE_NOT_ELIGIBLE";
        if (media.getMediaStatus() == MediaStatus.REJECTED) return "MEDIA_REJECTED";
        if (media.getValidatedAt() == null || media.getValidationError() != null) return "MEDIA_NOT_VALIDATED";
        if (media.getMediaStatus() == MediaStatus.AVAILABLE) return null;
        if (media.getMediaStatus() == MediaStatus.PENDING_MANAGER_APPROVAL)
            return finalApproval ? "MEDIA_APPROVAL_REQUIRED" : null;
        return "MEDIA_NOT_VALIDATED";
    }
    public boolean attachable(MediaAsset media) {
        return "DRONE_CAMERA".equals(media.getSourceType())
                && Set.of("IMAGE", "VIDEO").contains(media.getType() == null ? "" : media.getType())
                && media.getMediaStatus() != null && media.getMediaStatus() != MediaStatus.REJECTED;
    }
}
