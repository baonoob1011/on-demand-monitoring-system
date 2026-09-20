package com.ondemandmonitoring.planning.dto;

public record PairedScenarioObservation(
        String scenarioId,
        String scenarioLabel,
        Double shortestDistanceM,
        Double energyAwareDistanceM,
        Double distanceDifferenceM,
        Double distanceDifferencePercent,
        Double shortestWorldZM,
        Double energyAwareWorldZM,
        Double altitudeDifferenceM,
        Double shortestDurationSec,
        Double energyAwareDurationSec,
        Double durationDifferenceSec,
        Double durationDifferencePercent,
        Double shortestEnergyMah,
        Double energyAwareEnergyMah,
        Double energySavingMah,
        Double energySavingPercent,
        Long shortestPlanningTimeMs,
        Long energyAwarePlanningTimeMs,
        Long planningTimeDifferenceMs,
        boolean energySaving,
        boolean approximatelyEqualEnergy,
        boolean longerButLowerEnergy,
        boolean lowerAltitudeAndLowerEnergy) {
}
