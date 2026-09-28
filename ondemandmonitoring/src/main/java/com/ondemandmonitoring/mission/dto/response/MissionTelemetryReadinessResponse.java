package com.ondemandmonitoring.mission.dto.response;

import java.time.Instant;

public record MissionTelemetryReadinessResponse(
        String deviceId,
        boolean ready,
        Instant lastTelemetryAt
) {
}
