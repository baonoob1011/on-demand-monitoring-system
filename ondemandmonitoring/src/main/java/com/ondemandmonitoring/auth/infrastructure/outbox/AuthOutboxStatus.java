package com.ondemandmonitoring.auth.infrastructure.outbox;

public enum AuthOutboxStatus {
    PENDING,
    PROCESSING,
    COMPLETED,
    FAILED
}
