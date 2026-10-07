package com.ondemandmonitoring.mission.dto.response;

import com.ondemandmonitoring.order.enums.ExecutionPlanStatus;
import java.time.Instant;

public record ExecutionPlanAmendResponse(String orderId, ExecutionPlanStatus executionPlanStatus,
        long executionPlanRevision, Instant amendedAt, String amendmentReason) {}
