package com.ondemandmonitoring.order.dto.request;

import com.ondemandmonitoring.order.enums.FlightAreaReviewStatus;
import jakarta.validation.constraints.NotNull;

public record FlightAreaReviewRequest(
        @NotNull FlightAreaReviewStatus status,
        Boolean requestedScopeFeasible,
        String reason,
        String note) {
}
