package com.ondemandmonitoring.planning.dto;

public record PairedHypothesisTestResult(
        String testName,
        String alternative,
        int originalPairCount,
        int zeroDifferenceCount,
        int effectiveN,
        Double statistic,
        Integer degreesOfFreedom,
        Double pValue,
        double alpha,
        boolean rejectNull,
        String zeroHandling,
        String tieHandling) {
}
