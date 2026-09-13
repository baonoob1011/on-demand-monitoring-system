package com.ondemandmonitoring.media.domain;

public enum MediaStatus {
    UPLOAD_PENDING,
    UPLOADING,
    VALIDATING,
    RETRY_REQUIRED,
    AVAILABLE,
    MANUAL_UPLOAD_REQUIRED,
    REJECTED
}
