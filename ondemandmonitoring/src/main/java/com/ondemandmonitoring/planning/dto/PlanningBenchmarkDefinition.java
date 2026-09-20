package com.ondemandmonitoring.planning.dto;

public record PlanningBenchmarkDefinition(
        String benchmarkId,
        double homeX,
        double homeY,
        double samplingSpacingM,
        int targetScenarioCount,
        double minimumTargetDistanceFromHomeM,
        int stratumColumns,
        int stratumRows) {

    public PlanningBenchmarkDefinition(
            String benchmarkId,
            double homeX,
            double homeY,
            double samplingSpacingM,
            int targetScenarioCount) {
        this(benchmarkId, homeX, homeY, samplingSpacingM, targetScenarioCount, 50.0, 5, 5);
    }
}
