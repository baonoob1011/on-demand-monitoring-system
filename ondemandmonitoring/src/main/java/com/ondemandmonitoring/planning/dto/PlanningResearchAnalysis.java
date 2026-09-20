package com.ondemandmonitoring.planning.dto;

import java.util.List;

public record PlanningResearchAnalysis(
        String experimentId,
        String experimentName,
        int scenarioCount,
        int datasetRowCount,
        List<AlgorithmDescriptiveStatistics> algorithms,
        FeasibilityPairSummary shortestVsEnergyAwareFeasibility,
        PairedAlgorithmAnalysis shortestVsEnergyAware) {

    public PlanningResearchAnalysis {
        algorithms = List.copyOf(algorithms);
    }
}
