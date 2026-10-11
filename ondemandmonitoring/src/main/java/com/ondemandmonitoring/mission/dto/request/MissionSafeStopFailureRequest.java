package com.ondemandmonitoring.mission.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record MissionSafeStopFailureRequest(@NotBlank @Size(max=1000) String reason) {}
