package com.ondemandmonitoring.planning.dto;

public record EnergyEstimateRequest(
        double plannedDistanceM,
        double homeWorldZ,
        double plannedCruiseWorldZ,
        boolean includeAscend,
        boolean includeDescend) {
}
