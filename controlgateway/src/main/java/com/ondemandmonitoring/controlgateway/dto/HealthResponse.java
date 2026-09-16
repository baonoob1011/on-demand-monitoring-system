package com.ondemandmonitoring.controlgateway.dto;

import java.time.Instant;

public record HealthResponse(
        String status,
        boolean ready,
        boolean recording,
        Instant timestamp
) {
}
