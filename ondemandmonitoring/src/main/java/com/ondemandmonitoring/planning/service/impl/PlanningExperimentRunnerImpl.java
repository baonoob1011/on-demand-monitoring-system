package com.ondemandmonitoring.planning.service.impl;

import com.ondemandmonitoring.mission.enums.FeasibilityStatus;
import com.ondemandmonitoring.mission.enums.PlanningAlgorithm;
import com.ondemandmonitoring.planning.dto.AlgorithmExperimentSummary;
import com.ondemandmonitoring.planning.dto.AlgorithmPlanningResult;
import com.ondemandmonitoring.planning.dto.PlanningComparisonInput;
import com.ondemandmonitoring.planning.dto.PlanningComparisonResult;
import com.ondemandmonitoring.planning.dto.PlanningExperimentDatasetRow;
import com.ondemandmonitoring.planning.dto.PlanningExperimentDefinition;
import com.ondemandmonitoring.planning.dto.PlanningExperimentResult;
import com.ondemandmonitoring.planning.dto.PlanningExperimentScenario;
import com.ondemandmonitoring.planning.dto.PlanningExperimentScenarioResult;
import com.ondemandmonitoring.planning.dto.PlanningExperimentSummary;
import com.ondemandmonitoring.planning.dto.PlanningTradeoff;
import com.ondemandmonitoring.planning.dto.response.EnvironmentSample;
import com.ondemandmonitoring.planning.service.PlanningComparisonService;
import com.ondemandmonitoring.planning.service.PlanningEnvironment;
import com.ondemandmonitoring.planning.service.PlanningExperimentRunner;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlanningExperimentRunnerImpl implements PlanningExperimentRunner {

    private static final double EQUALITY_TOLERANCE = 1.0e-9;

    private final PlanningComparisonService comparisonService;
    private final PlanningEnvironment planningEnvironment;

    public PlanningExperimentRunnerImpl(
            PlanningComparisonService comparisonService,
            PlanningEnvironment planningEnvironment) {
        this.comparisonService = comparisonService;
        this.planningEnvironment = planningEnvironment;
    }

    @Override
    @Transactional(readOnly = true)
    public PlanningExperimentResult run(PlanningExperimentDefinition definition) {
        validate(definition);
        EnvironmentSample home = planningEnvironment.sample(definition.homeX(), definition.homeY());
        if (!home.insideWorldBounds()) throw new IllegalArgumentException("Experiment home is outside the planning world.");
        if (home.restricted()) throw new IllegalArgumentException("Experiment home is inside a restricted zone.");
        if (home.surfaceElevationM() == null) {
            throw new IllegalArgumentException("Experiment home has no planning surface elevation.");
        }

        long started = System.nanoTime();
        List<PlanningExperimentScenarioResult> scenarioResults = new ArrayList<>();
        List<PlanningExperimentDatasetRow> dataset = new ArrayList<>();
        for (PlanningExperimentScenario scenario : definition.scenarios()) {
            PlanningComparisonResult comparison = comparisonService.compare(new PlanningComparisonInput(
                    scenario.scenarioId(), definition.homeX(), definition.homeY(),
                    scenario.targetX(), scenario.targetY(), null));
            scenarioResults.add(new PlanningExperimentScenarioResult(scenario, comparison));
            for (AlgorithmPlanningResult result : comparison.results()) {
                dataset.add(row(definition.experimentId(), scenario, comparison, result));
            }
        }
        return new PlanningExperimentResult(
                definition,
                home.surfaceElevationM(),
                scenarioResults,
                dataset,
                summarize(dataset, scenarioResults),
                elapsedMs(started));
    }

    private PlanningExperimentDatasetRow row(String experimentId, PlanningExperimentScenario scenario,
            PlanningComparisonResult comparison, AlgorithmPlanningResult result) {
        return new PlanningExperimentDatasetRow(
                experimentId, scenario.scenarioId(), comparison.homeX(), comparison.homeY(),
                scenario.targetX(), scenario.targetY(), result.algorithm(), result.feasibilityStatus(),
                result.plannedDistanceM(), result.maxPlannedAltitudeM(), result.plannedDurationSec(),
                result.estimatedEnergyMah(), result.estimatedBatteryUsedPercent(),
                result.requiredBatteryPercent(), result.planningTimeMs(), result.waypointCount(),
                result.failureReason());
    }

    private PlanningExperimentSummary summarize(
            List<PlanningExperimentDatasetRow> dataset,
            List<PlanningExperimentScenarioResult> scenarios) {
        Map<PlanningAlgorithm, List<PlanningExperimentDatasetRow>> byAlgorithm =
                new EnumMap<>(PlanningAlgorithm.class);
        for (PlanningAlgorithm algorithm : PlanningAlgorithm.values()) byAlgorithm.put(algorithm, new ArrayList<>());
        dataset.forEach(row -> byAlgorithm.get(row.algorithm()).add(row));
        List<AlgorithmExperimentSummary> algorithms = List.of(
                algorithmSummary(PlanningAlgorithm.DIRECT, byAlgorithm.get(PlanningAlgorithm.DIRECT)),
                algorithmSummary(PlanningAlgorithm.ASTAR_SHORTEST, byAlgorithm.get(PlanningAlgorithm.ASTAR_SHORTEST)),
                algorithmSummary(PlanningAlgorithm.ASTAR_ENERGY_AWARE,
                        byAlgorithm.get(PlanningAlgorithm.ASTAR_ENERGY_AWARE)));

        List<PlanningTradeoff> comparable = scenarios.stream()
                .map(value -> value.comparison().energyAwareVsShortest())
                .filter(value -> value.energySavingMah() != null
                        && value.energySavingPercent() != null
                        && value.distanceDifferenceM() != null
                        && value.distanceDifferencePercent() != null
                        && value.durationDifferenceSec() != null
                        && value.altitudeDifferenceM() != null
                        && value.planningTimeDifferenceMs() != null)
                .toList();
        int saving = 0;
        int equal = 0;
        int longerLower = 0;
        int lowerAltitudeLower = 0;
        for (PlanningExperimentScenarioResult scenario : scenarios) {
            AlgorithmPlanningResult shortest = find(scenario.comparison(), PlanningAlgorithm.ASTAR_SHORTEST);
            AlgorithmPlanningResult aware = find(scenario.comparison(), PlanningAlgorithm.ASTAR_ENERGY_AWARE);
            if (shortest.estimatedEnergyMah() == null || aware.estimatedEnergyMah() == null) continue;
            double energyDifference = shortest.estimatedEnergyMah() - aware.estimatedEnergyMah();
            if (energyDifference > EQUALITY_TOLERANCE) saving++;
            if (Math.abs(energyDifference) <= EQUALITY_TOLERANCE) equal++;
            if (aware.plannedDistanceM() > shortest.plannedDistanceM() + EQUALITY_TOLERANCE
                    && energyDifference > EQUALITY_TOLERANCE) longerLower++;
            if (aware.maxPlannedAltitudeM() < shortest.maxPlannedAltitudeM() - EQUALITY_TOLERANCE
                    && energyDifference > EQUALITY_TOLERANCE) lowerAltitudeLower++;
        }
        return new PlanningExperimentSummary(
                algorithms, comparable.size(),
                meanTradeoff(comparable, PlanningTradeoff::energySavingMah),
                meanTradeoff(comparable, PlanningTradeoff::energySavingPercent),
                meanTradeoff(comparable, PlanningTradeoff::distanceDifferenceM),
                meanTradeoff(comparable, PlanningTradeoff::distanceDifferencePercent),
                meanTradeoff(comparable, PlanningTradeoff::durationDifferenceSec),
                meanTradeoff(comparable, PlanningTradeoff::altitudeDifferenceM),
                meanTradeoff(comparable, value -> value.planningTimeDifferenceMs().doubleValue()),
                saving, equal, longerLower, lowerAltitudeLower);
    }

    private AlgorithmExperimentSummary algorithmSummary(
            PlanningAlgorithm algorithm, List<PlanningExperimentDatasetRow> rows) {
        List<PlanningExperimentDatasetRow> feasible = rows.stream()
                .filter(row -> row.feasibilityStatus() == FeasibilityStatus.FEASIBLE).toList();
        int total = rows.size();
        int feasibleCount = feasible.size();
        return new AlgorithmExperimentSummary(
                algorithm, total, feasibleCount, total - feasibleCount,
                total == 0 ? 0.0 : feasibleCount * 100.0 / total,
                mean(feasible, PlanningExperimentDatasetRow::plannedDistanceM),
                mean(feasible, PlanningExperimentDatasetRow::plannedDurationSec),
                mean(feasible, PlanningExperimentDatasetRow::estimatedEnergyMah),
                mean(feasible, PlanningExperimentDatasetRow::estimatedBatteryUsedPercent),
                mean(rows, row -> row.planningTimeMs().doubleValue()),
                rows.stream().map(PlanningExperimentDatasetRow::planningTimeMs).min(Long::compareTo).orElse(null),
                rows.stream().map(PlanningExperimentDatasetRow::planningTimeMs).max(Long::compareTo).orElse(null));
    }

    private AlgorithmPlanningResult find(PlanningComparisonResult comparison, PlanningAlgorithm algorithm) {
        return comparison.results().stream().filter(value -> value.algorithm() == algorithm).findFirst().orElseThrow();
    }

    private Double mean(List<PlanningExperimentDatasetRow> rows,
            java.util.function.Function<PlanningExperimentDatasetRow, Double> getter) {
        return rows.isEmpty() ? null : rows.stream().map(getter).filter(java.util.Objects::nonNull)
                .mapToDouble(Double::doubleValue).average().stream().boxed().findFirst().orElse(null);
    }

    private Double meanTradeoff(List<PlanningTradeoff> rows,
            java.util.function.Function<PlanningTradeoff, Double> getter) {
        return rows.isEmpty() ? null : rows.stream().map(getter).mapToDouble(Double::doubleValue).average().orElseThrow();
    }

    private void validate(PlanningExperimentDefinition definition) {
        if (definition == null) throw new IllegalArgumentException("Experiment definition is required.");
        if (definition.experimentId() == null || definition.experimentId().isBlank()) {
            throw new IllegalArgumentException("Experiment ID is required.");
        }
        if (!Double.isFinite(definition.homeX()) || !Double.isFinite(definition.homeY())) {
            throw new IllegalArgumentException("Experiment home coordinates must be finite.");
        }
        if (definition.scenarios() == null || definition.scenarios().isEmpty()) {
            throw new IllegalArgumentException("Experiment must contain at least one scenario.");
        }
        Set<String> ids = new HashSet<>();
        for (PlanningExperimentScenario scenario : definition.scenarios()) {
            if (scenario == null) throw new IllegalArgumentException("Experiment scenario is required.");
            if (scenario.scenarioId() == null || scenario.scenarioId().isBlank()) {
                throw new IllegalArgumentException("Scenario ID is required.");
            }
            if (!ids.add(scenario.scenarioId())) {
                throw new IllegalArgumentException("Duplicate scenario ID: " + scenario.scenarioId());
            }
            if (!Double.isFinite(scenario.targetX()) || !Double.isFinite(scenario.targetY())) {
                throw new IllegalArgumentException("Scenario target coordinates must be finite: " + scenario.scenarioId());
            }
        }
    }

    private long elapsedMs(long started) {
        return Math.max(0L, (System.nanoTime() - started) / 1_000_000L);
    }
}
