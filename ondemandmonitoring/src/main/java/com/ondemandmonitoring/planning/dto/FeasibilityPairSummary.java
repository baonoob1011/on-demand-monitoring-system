package com.ondemandmonitoring.planning.dto;

public record FeasibilityPairSummary(
        int bothShortestAndEnergyAwareFeasible,
        int shortestOnlyFeasible,
        int energyAwareOnlyFeasible,
        int neitherFeasible) {
}
