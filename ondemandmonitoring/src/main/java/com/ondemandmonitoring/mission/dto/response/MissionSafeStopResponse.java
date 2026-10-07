package com.ondemandmonitoring.mission.dto.response;

import java.time.Instant;
public record MissionSafeStopResponse(String missionId, com.ondemandmonitoring.mission.enums.MissionSafeStopStatus status, Instant requestedAt,
        Instant confirmedAt, Instant cleanedAt, String failureReason, boolean requiresLanding) {}
