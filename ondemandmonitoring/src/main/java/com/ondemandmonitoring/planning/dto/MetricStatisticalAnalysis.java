package com.ondemandmonitoring.planning.dto;

public record MetricStatisticalAnalysis(
        String metricKey,
        String displayName,
        String unit,
        String signConvention,
        boolean primaryEndpoint,
        MetricDistributionSummary distribution,
        NormalityAssessment normality,
        PairedHypothesisTestResult hypothesisTest,
        EffectSizeResult effectSize,
        ConfidenceInterval meanConfidenceInterval,
        Double rawPValue,
        Double holmAdjustedPValue,
        boolean holmRejected) {
}
