package com.ondemandmonitoring.planning.dto;

import java.util.List;

public record MetricDistributionSummary(
        int n,
        Double mean,
        Double median,
        Double sampleStandardDeviation,
        Double q1,
        Double q3,
        Double iqr,
        Double min,
        Double max,
        Double skewness,
        Double kurtosis,
        OutlierSummary outliers,
        List<Double> sortedValues) {

    public MetricDistributionSummary {
        sortedValues = List.copyOf(sortedValues);
    }
}
