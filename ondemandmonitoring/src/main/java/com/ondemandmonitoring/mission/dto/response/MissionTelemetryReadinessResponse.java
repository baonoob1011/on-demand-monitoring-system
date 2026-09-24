package com.ondemandmonitoring.mission.dto.response;

import java.time.Instant;

public record MissionTelemetryReadinessResponse(
        String droneCode,
        boolean ready,
        Instant lastTelemetryAt
) {
}
