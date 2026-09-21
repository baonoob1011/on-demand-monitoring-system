package com.ondemandmonitoring.planning.service.impl;

import com.ondemandmonitoring.mission.enums.FeasibilityStatus;
import com.ondemandmonitoring.mission.enums.PlanningAlgorithm;
import com.ondemandmonitoring.planning.dto.AlgorithmDescriptiveStatistics;
import com.ondemandmonitoring.planning.dto.FeasibilityPairSummary;
import com.ondemandmonitoring.planning.dto.NumericDescriptiveStatistics;
import com.ondemandmonitoring.planning.dto.PairedAlgorithmAnalysis;
import com.ondemandmonitoring.planning.dto.PairedScenarioObservation;
import com.ondemandmonitoring.planning.dto.PlanningExperimentDatasetRow;
import com.ondemandmonitoring.planning.dto.PlanningExperimentResult;
import com.ondemandmonitoring.planning.dto.PlanningResearchAnalysis;
import com.ondemandmonitoring.planning.service.PlanningResearchAnalysisService;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import org.springframework.stereotype.Service;

@Service
public class PlanningResearchAnalysisServiceImpl implements PlanningResearchAnalysisService {

    private static final double EPSILON = 1.0e-9;
    private static final String PLANNING_TIME_POPULATION = "all executed algorithm rows";

    @Override
    public PlanningResearchAnalysis analyze(PlanningExperimentResult result) {
        if (result == null) throw new IllegalArgumentException("Planning experiment result is required.");
        Map<PlanningAlgorithm, List<PlanningExperimentDatasetRow>> byAlgorithm = new EnumMap<>(PlanningAlgorithm.class);
        for (PlanningAlgorithm algorithm : PlanningAlgorithm.values()) byAlgorithm.put(algorithm, new ArrayList<>());
        result.dataset().forEach(row -> byAlgorithm.computeIfAbsent(row.algorithm(), ignored -> new ArrayList<>()).add(row));

        List<AlgorithmDescriptiveStatistics> algorithms = List.of(
                describe(PlanningAlgorithm.DIRECT, byAlgorithm.get(PlanningAlgorithm.DIRECT)),
                describe(PlanningAlgorithm.ASTAR_SHORTEST, byAlgorithm.get(PlanningAlgorithm.ASTAR_SHORTEST)),
                describe(PlanningAlgorithm.ASTAR_ENERGY_AWARE, byAlgorithm.get(PlanningAlgorithm.ASTAR_ENERGY_AWARE)));
        FeasibilityPairSummary feasibility = feasibility(result);
        PairedAlgorithmAnalysis paired = pairedAnalysis(result);
        return new PlanningResearchAnalysis(
                result.definition().experimentId(),
                result.definition().name(),
                result.definition().scenarios().size(),
                result.dataset().size(),
                algorithms,
                feasibility,
                paired);
    }

    private AlgorithmDescriptiveStatistics describe(
            PlanningAlgorithm algorithm, List<PlanningExperimentDatasetRow> rows) {
        List<PlanningExperimentDatasetRow> all = rows == null ? List.of() : rows;
        List<PlanningExperimentDatasetRow> feasible = all.stream()
                .filter(row -> row.feasibilityStatus() == FeasibilityStatus.FEASIBLE)
                .toList();
        int total = all.size();
        int feasibleCount = feasible.size();
        return new AlgorithmDescriptiveStatistics(
                algorithm,
                total,
                feasibleCount,
                total - feasibleCount,
                total == 0 ? 0.0 : feasibleCount * 100.0 / total,
                stats(feasible, PlanningExperimentDatasetRow::plannedDistanceM),
                stats(feasible, PlanningExperimentDatasetRow::plannedDurationSec),
                stats(feasible, PlanningExperimentDatasetRow::estimatedEnergyMah),
                stats(feasible, PlanningExperimentDatasetRow::estimatedBatteryUsedPercent),
                stats(all, row -> row.planningTimeMs() == null ? null : row.planningTimeMs().doubleValue()),
                PLANNING_TIME_POPULATION);
    }

    private FeasibilityPairSummary feasibility(PlanningExperimentResult result) {
        int both = 0;
        int shortestOnly = 0;
        int awareOnly = 0;
        int neither = 0;
        for (String scenarioId : scenarioIds(result)) {
            boolean shortest = feasible(row(result, scenarioId, PlanningAlgorithm.ASTAR_SHORTEST));
            boolean aware = feasible(row(result, scenarioId, PlanningAlgorithm.ASTAR_ENERGY_AWARE));
            if (shortest && aware) both++;
            else if (shortest) shortestOnly++;
            else if (aware) awareOnly++;
            else neither++;
        }
        return new FeasibilityPairSummary(both, shortestOnly, awareOnly, neither);
    }

    private PairedAlgorithmAnalysis pairedAnalysis(PlanningExperimentResult result) {
        List<PairedScenarioObservation> observations = new ArrayList<>();
        for (String scenarioId : scenarioIds(result)) {
            PlanningExperimentDatasetRow shortest = row(result, scenarioId, PlanningAlgorithm.ASTAR_SHORTEST);
            PlanningExperimentDatasetRow aware = row(result, scenarioId, PlanningAlgorithm.ASTAR_ENERGY_AWARE);
            if (!comparable(shortest, aware)) continue;
            observations.add(observation(result, shortest, aware));
        }
        int saving = 0;
        int equal = 0;
        int higher = 0;
        int longerLower = 0;
        int lowerAltitudeLower = 0;
        for (PairedScenarioObservation observation : observations) {
            if (observation.energySaving()) saving++;
            if (observation.approximatelyEqualEnergy()) equal++;
            if (observation.energySavingMah() < -EPSILON) higher++;
            if (observation.longerButLowerEnergy()) longerLower++;
            if (observation.lowerAltitudeAndLowerEnergy()) lowerAltitudeLower++;
        }
        return new PairedAlgorithmAnalysis(
                PlanningAlgorithm.ASTAR_SHORTEST,
                PlanningAlgorithm.ASTAR_ENERGY_AWARE,
                observations.size(),
                saving,
                equal,
                higher,
                longerLower,
                lowerAltitudeLower,
                stats(observations, PairedScenarioObservation::energySavingMah),
                stats(observations, PairedScenarioObservation::energySavingPercent),
                stats(observations, PairedScenarioObservation::distanceDifferenceM),
                stats(observations, PairedScenarioObservation::distanceDifferencePercent),
                stats(observations, PairedScenarioObservation::durationDifferenceSec),
                stats(observations, PairedScenarioObservation::durationDifferencePercent),
                stats(observations, PairedScenarioObservation::altitudeDifferenceM),
                stats(observations, value -> value.planningTimeDifferenceMs() == null
                        ? null : value.planningTimeDifferenceMs().doubleValue()),
                observations);
    }

    private PairedScenarioObservation observation(
            PlanningExperimentResult result,
            PlanningExperimentDatasetRow shortest,
            PlanningExperimentDatasetRow aware) {
        Double energySavingMah = shortest.estimatedEnergyMah() - aware.estimatedEnergyMah();
        Double distanceDifferenceM = aware.plannedDistanceM() - shortest.plannedDistanceM();
        Double durationDifferenceSec = aware.plannedDurationSec() - shortest.plannedDurationSec();
        Double altitudeDifferenceM = aware.maxPlannedAltitudeM() - shortest.maxPlannedAltitudeM();
        Long planningTimeDifferenceMs = aware.planningTimeMs() - shortest.planningTimeMs();
        boolean saving = energySavingMah > EPSILON;
        boolean equal = Math.abs(energySavingMah) <= EPSILON;
        return new PairedScenarioObservation(
                shortest.scenarioId(),
                scenarioLabel(result, shortest.scenarioId()),
                shortest.plannedDistanceM(),
                aware.plannedDistanceM(),
                distanceDifferenceM,
                percent(distanceDifferenceM, shortest.plannedDistanceM()),
                shortest.maxPlannedAltitudeM(),
                aware.maxPlannedAltitudeM(),
                altitudeDifferenceM,
                shortest.plannedDurationSec(),
                aware.plannedDurationSec(),
                durationDifferenceSec,
                percent(durationDifferenceSec, shortest.plannedDurationSec()),
                shortest.estimatedEnergyMah(),
                aware.estimatedEnergyMah(),
                energySavingMah,
                percent(energySavingMah, shortest.estimatedEnergyMah()),
                shortest.planningTimeMs(),
                aware.planningTimeMs(),
                planningTimeDifferenceMs,
                saving,
                equal,
                aware.plannedDistanceM() > shortest.plannedDistanceM() + EPSILON && saving,
                aware.maxPlannedAltitudeM() < shortest.maxPlannedAltitudeM() - EPSILON && saving);
    }

    private <T> NumericDescriptiveStatistics stats(List<T> rows, Function<T, Double> getter) {
        List<Double> values = rows.stream()
                .map(getter)
                .filter(Objects::nonNull)
                .filter(Double::isFinite)
                .sorted(Comparator.naturalOrder())
                .toList();
        int count = values.size();
        if (count == 0) return new NumericDescriptiveStatistics(0, null, null, null, null, null);
        double mean = values.stream().mapToDouble(Double::doubleValue).average().orElseThrow();
        double median = count % 2 == 1
                ? values.get(count / 2)
                : (values.get(count / 2 - 1) + values.get(count / 2)) / 2.0;
        Double standardDeviation = null;
        if (count >= 2) {
            double sumSquared = values.stream().mapToDouble(value -> Math.pow(value - mean, 2.0)).sum();
            standardDeviation = Math.sqrt(sumSquared / (count - 1));
        }
        return new NumericDescriptiveStatistics(
                count, mean, median, values.get(0), values.get(count - 1), standardDeviation);
    }

    private List<String> scenarioIds(PlanningExperimentResult result) {
        return result.definition().scenarios().stream().map(scenario -> scenario.scenarioId()).toList();
    }

    private PlanningExperimentDatasetRow row(
            PlanningExperimentResult result, String scenarioId, PlanningAlgorithm algorithm) {
        return result.dataset().stream()
                .filter(value -> value.scenarioId().equals(scenarioId) && value.algorithm() == algorithm)
                .findFirst()
                .orElse(null);
    }

    private boolean comparable(PlanningExperimentDatasetRow shortest, PlanningExperimentDatasetRow aware) {
        return feasible(shortest)
                && feasible(aware)
                && present(shortest.plannedDistanceM(), shortest.maxPlannedAltitudeM(), shortest.plannedDurationSec(),
                        shortest.estimatedEnergyMah())
                && present(aware.plannedDistanceM(), aware.maxPlannedAltitudeM(), aware.plannedDurationSec(),
                        aware.estimatedEnergyMah())
                && shortest.planningTimeMs() != null
                && aware.planningTimeMs() != null;
    }

    private boolean feasible(PlanningExperimentDatasetRow row) {
        return row != null && row.feasibilityStatus() == FeasibilityStatus.FEASIBLE;
    }

    private boolean present(Double... values) {
        for (Double value : values) {
            if (value == null || !Double.isFinite(value)) return false;
        }
        return true;
    }

    private Double percent(Double numerator, Double denominator) {
        if (numerator == null || denominator == null || Math.abs(denominator) <= EPSILON) return null;
        double value = numerator / denominator * 100.0;
        return Double.isFinite(value) ? value : null;
    }

    private String scenarioLabel(PlanningExperimentResult result, String scenarioId) {
        return result.definition().scenarios().stream()
                .filter(scenario -> scenario.scenarioId().equals(scenarioId))
                .map(scenario -> scenario.label())
                .findFirst()
                .orElse(null);
    }
}
