package com.ondemandmonitoring.media.domain;

public enum MediaStatus {
    UPLOAD_PENDING,
    UPLOADING,
    VALIDATING,
    RETRY_REQUIRED,
    MANUAL_UPLOAD_REQUIRED,
    AVAILABLE,
    REJECTED
}
