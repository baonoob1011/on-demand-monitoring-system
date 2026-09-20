package com.ondemandmonitoring.planning.dto;

public record ConfidenceInterval(
        double confidenceLevel,
        Double lower,
        Double upper,
        String method,
        Integer iterations,
        Long seed) {
}
