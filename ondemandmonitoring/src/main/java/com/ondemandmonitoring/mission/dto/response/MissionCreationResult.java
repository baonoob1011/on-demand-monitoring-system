package com.ondemandmonitoring.mission.dto.response;

/** Command outcome only; controllers keep the existing MissionResponse wire shape. */
public record MissionCreationResult(MissionResponse mission, boolean created) {}
