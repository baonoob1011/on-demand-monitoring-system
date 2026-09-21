package com.ondemandmonitoring.planning.dto;

import java.util.List;

public record PlanningBenchmarkGenerationResult(
        PlanningBenchmarkDefinition definition,
        List<PlanningExperimentScenario> scenarios,
        List<PlanningBenchmarkCandidate> selectedCandidates,
        int candidateCount,
        int validCandidateCount,
        int acceptedCount,
        int rejectedOutsideCount,
        int rejectedNoDataCount,
        int rejectedRestrictedCount,
        int rejectedTooCloseToHomeCount,
        int occupiedStrataCount,
        int totalStrataCount,
        long generationElapsedMs) {

    public PlanningBenchmarkGenerationResult {
        scenarios = List.copyOf(scenarios);
        selectedCandidates = List.copyOf(selectedCandidates);
    }
}
