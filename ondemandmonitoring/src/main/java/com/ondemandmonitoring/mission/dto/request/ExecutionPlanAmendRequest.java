package com.ondemandmonitoring.mission.dto.request;

import jakarta.validation.constraints.*;

public record ExecutionPlanAmendRequest(@NotNull @PositiveOrZero Long expectedPlanRevision,
        @NotBlank @Size(max = 1000) String reason) {}
