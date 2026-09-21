package com.ondemandmonitoring.planning.dto;

import com.ondemandmonitoring.mission.enums.PlanningAlgorithm;
import java.util.List;

public record PairedAlgorithmAnalysis(
        PlanningAlgorithm baselineAlgorithm,
        PlanningAlgorithm comparisonAlgorithm,
        int comparableScenarioCount,
        int energySavingScenarioCount,
        int equalEnergyScenarioCount,
        int higherEnergyScenarioCount,
        int longerButLowerEnergyCount,
        int lowerAltitudeAndLowerEnergyCount,
        NumericDescriptiveStatistics energySavingMah,
        NumericDescriptiveStatistics energySavingPercent,
        NumericDescriptiveStatistics distanceDifferenceM,
        NumericDescriptiveStatistics distanceDifferencePercent,
        NumericDescriptiveStatistics durationDifferenceSec,
        NumericDescriptiveStatistics durationDifferencePercent,
        NumericDescriptiveStatistics altitudeDifferenceM,
        NumericDescriptiveStatistics planningTimeDifferenceMs,
        List<PairedScenarioObservation> observations) {

    public PairedAlgorithmAnalysis {
        observations = List.copyOf(observations);
    }
}
