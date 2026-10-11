package com.ondemandmonitoring.mission.dto.request;

import jakarta.validation.constraints.*;
public record MissionSafeStopConfirmationRequest(@NotNull @AssertTrue Boolean landedAndDisarmed,
        @NotNull @AssertTrue Boolean controlSessionClosed, @NotBlank @Size(max=1000) String confirmation) {}
