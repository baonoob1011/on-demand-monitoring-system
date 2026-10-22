package com.ondemandmonitoring.mission.dto.response;

/** @deprecated Mission creation now returns {@link MissionResponse} directly. */
@Deprecated
public record MissionCreationResult(MissionResponse mission, boolean created) {}
