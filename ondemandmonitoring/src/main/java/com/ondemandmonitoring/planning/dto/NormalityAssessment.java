package com.ondemandmonitoring.planning.dto;

public record NormalityAssessment(
        String method,
        Double statistic,
        Double pValue,
        double alpha,
        boolean rejectNormality) {
}
