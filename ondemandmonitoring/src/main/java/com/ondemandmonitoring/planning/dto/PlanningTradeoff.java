package com.ondemandmonitoring.planning.dto;

public record PlanningTradeoff(
        Double energySavingMah,
        Double energySavingPercent,
        Double distanceDifferenceM,
        Double distanceDifferencePercent,
        Double durationDifferenceSec,
        Double durationDifferencePercent,
        Double altitudeDifferenceM,
        Long planningTimeDifferenceMs) {
}
