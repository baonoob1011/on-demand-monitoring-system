package com.ondemandmonitoring.planning.dto;

public record NumericDescriptiveStatistics(
        int count,
        Double mean,
        Double median,
        Double min,
        Double max,
        Double standardDeviation) {
}
