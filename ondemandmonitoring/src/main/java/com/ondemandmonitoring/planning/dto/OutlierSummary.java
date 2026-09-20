package com.ondemandmonitoring.planning.dto;

public record OutlierSummary(
        Double lowerFence,
        Double upperFence,
        int outlierCount) {
}
