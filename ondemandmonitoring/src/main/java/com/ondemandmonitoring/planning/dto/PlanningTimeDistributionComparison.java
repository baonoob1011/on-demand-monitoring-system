package com.ondemandmonitoring.planning.dto;

public record PlanningTimeDistributionComparison(
        MetricDistributionSummary shortestPlanningTimeMs,
        MetricDistributionSummary energyAwarePlanningTimeMs,
        MetricDistributionSummary pairedDifferenceMs) {
}
