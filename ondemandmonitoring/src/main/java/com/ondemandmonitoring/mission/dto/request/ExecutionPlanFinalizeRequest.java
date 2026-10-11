package com.ondemandmonitoring.mission.dto.request;

import jakarta.validation.constraints.*;

public record ExecutionPlanFinalizeRequest(
        @NotNull @PositiveOrZero Long expectedPlanRevision,
        @NotNull Boolean decompositionConfirmed) {}
