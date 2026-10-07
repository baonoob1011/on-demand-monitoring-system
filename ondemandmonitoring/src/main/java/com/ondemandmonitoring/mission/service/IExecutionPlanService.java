package com.ondemandmonitoring.mission.service;

import com.ondemandmonitoring.mission.dto.request.ExecutionPlanFinalizeRequest;
import com.ondemandmonitoring.mission.dto.response.ExecutionPlanResponse;

public interface IExecutionPlanService {
    ExecutionPlanResponse get(String orderId);
    ExecutionPlanResponse finalizePlan(String orderId, ExecutionPlanFinalizeRequest request);
    com.ondemandmonitoring.mission.dto.response.ExecutionPlanAmendResponse amend(String orderId,
            com.ondemandmonitoring.mission.dto.request.ExecutionPlanAmendRequest request);
}
