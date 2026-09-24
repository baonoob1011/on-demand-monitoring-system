package com.ondemandmonitoring.planning.dto;

import java.util.List;

public record PlanningStatisticalAnalysis(
        String experimentId,
        int scenarioCount,
        int pairedScenarioCount,
        double alpha,
        double epsilon,
        String normalityMethod,
        String testSelectionRule,
        int bootstrapIterations,
        long bootstrapSeed,
        MetricStatisticalAnalysis primaryEnergyAnalysis,
        ConfidenceInterval energySavingPercentMeanConfidenceInterval,
        List<MetricStatisticalAnalysis> secondaryMetricAnalyses,
        PlanningTimeDistributionComparison planningTimeDistributions,
        long statisticalAnalysisElapsedMs) {

    public PlanningStatisticalAnalysis {
        secondaryMetricAnalyses = List.copyOf(secondaryMetricAnalyses);
    }
}
