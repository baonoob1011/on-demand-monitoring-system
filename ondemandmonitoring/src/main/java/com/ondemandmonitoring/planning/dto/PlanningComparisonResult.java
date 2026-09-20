package com.ondemandmonitoring.planning.dto;

import java.util.List;

public record PlanningComparisonResult(
        String missionId,
        String missionCode,
        double homeX,
        double homeY,
        double targetX,
        double targetY,
        double homeWorldZ,
        List<AlgorithmPlanningResult> results,
        PlanningTradeoff energyAwareVsShortest,
        long totalComparisonTimeMs) {

    public PlanningComparisonResult {
        results = List.copyOf(results);
    }
}
