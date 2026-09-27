package com.ondemandmonitoring.device.service;

import java.time.Duration;
import java.time.Instant;

public final class DeviceTelemetryFreshness {

    public static final Duration MAX_AGE = Duration.ofSeconds(45);

    private DeviceTelemetryFreshness() {
    }

    public static boolean isFresh(Instant updatedAt) {
        return updatedAt != null
                && Duration.between(updatedAt, Instant.now()).abs().compareTo(MAX_AGE) <= 0;
    }
}
