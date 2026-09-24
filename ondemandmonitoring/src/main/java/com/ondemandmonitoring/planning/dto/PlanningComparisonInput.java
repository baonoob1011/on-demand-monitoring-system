package com.ondemandmonitoring.planning.dto;

public record PlanningComparisonInput(
        String contextId,
        double homeX,
        double homeY,
        double targetX,
        double targetY,
        Double availableBatteryPercent) {
}
