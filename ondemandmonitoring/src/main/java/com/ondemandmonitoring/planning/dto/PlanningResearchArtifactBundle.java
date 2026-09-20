package com.ondemandmonitoring.planning.dto;

public record PlanningResearchArtifactBundle(
        String experimentId,
        String methodologySummaryMarkdown,
        String algorithmSummaryCsv,
        String pairedScenarioCsv,
        String statisticalSummaryCsv,
        String figureEnergySavingCsv,
        String figureDistanceEnergyTradeoffCsv,
        String figureWorldZDifferenceCsv,
        String figurePlanningTimeCsv,
        String figureEnergyDistributionCsv,
        String figurePlanningTimeDistributionCsv,
        String manifestJson) {
}
