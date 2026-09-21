package com.ondemandmonitoring.planning.dto;

import java.util.List;

public record PlanningExperimentResult(
        PlanningExperimentDefinition definition,
        double homeWorldZ,
        List<PlanningExperimentScenarioResult> scenarioResults,
        List<PlanningExperimentDatasetRow> dataset,
        PlanningExperimentSummary summary,
        long totalExperimentElapsedMs) {

    public PlanningExperimentResult {
        scenarioResults = List.copyOf(scenarioResults);
        dataset = List.copyOf(dataset);
    }
}
