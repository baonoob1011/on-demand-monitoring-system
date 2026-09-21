package com.ondemandmonitoring.planning.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.ondemandmonitoring.planning.dto.AlgorithmDescriptiveStatistics;
import com.ondemandmonitoring.planning.dto.MetricStatisticalAnalysis;
import com.ondemandmonitoring.planning.dto.PairedScenarioObservation;
import com.ondemandmonitoring.planning.dto.PlanningExperimentDatasetRow;
import com.ondemandmonitoring.planning.dto.PlanningExperimentResult;
import com.ondemandmonitoring.planning.dto.PlanningResearchAnalysis;
import com.ondemandmonitoring.planning.dto.PlanningResearchArtifactBundle;
import com.ondemandmonitoring.planning.dto.PlanningStatisticalAnalysis;
import com.ondemandmonitoring.planning.service.PlanningResearchArtifactService;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class PlanningResearchArtifactServiceImpl implements PlanningResearchArtifactService {

    private static final double EPSILON = 1.0e-9;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    @Override
    public PlanningResearchArtifactBundle build(
            PlanningExperimentResult experimentResult,
            PlanningResearchAnalysis researchAnalysis,
            PlanningStatisticalAnalysis statisticalAnalysis) {
        if (experimentResult == null) throw new IllegalArgumentException("Experiment result is required.");
        if (researchAnalysis == null) throw new IllegalArgumentException("Research analysis is required.");
        if (statisticalAnalysis == null) throw new IllegalArgumentException("Statistical analysis is required.");
        verifyConsistency(researchAnalysis);
        return new PlanningResearchArtifactBundle(
                experimentResult.definition().experimentId(),
                methodology(experimentResult, researchAnalysis, statisticalAnalysis),
                algorithmSummary(researchAnalysis),
                pairedScenarios(researchAnalysis),
                statisticalSummary(statisticalAnalysis),
                figureEnergySaving(researchAnalysis),
                figureDistanceEnergy(researchAnalysis),
                figureWorldZ(researchAnalysis),
                figurePlanningTime(researchAnalysis),
                figureEnergyDistribution(researchAnalysis),
                figurePlanningTimeDistribution(researchAnalysis),
                manifest(experimentResult, researchAnalysis, statisticalAnalysis));
    }

    private String methodology(PlanningExperimentResult experiment, PlanningResearchAnalysis research,
            PlanningStatisticalAnalysis statistics) {
        MetricStatisticalAnalysis energy = statistics.primaryEnergyAnalysis();
        return "# Methodology Summary\n\n"
                + "- Benchmark: " + experiment.definition().scenarios().size() + " deterministic scenarios.\n"
                + "- Algorithms: DIRECT, ASTAR_SHORTEST, ASTAR_ENERGY_AWARE.\n"
                + "- Paired comparison: same scenarios for ASTAR_SHORTEST and ASTAR_ENERGY_AWARE.\n"
                + "- Primary endpoint: predicted mission energy difference, shortestEnergy - energyAwareEnergy.\n"
                + "- Secondary endpoints: planned distance, planned duration, planned Gazebo World Z, planning time.\n"
                + "- Inference: alpha 0.05; Jarque-Bera normality assessment; two-sided paired t-test when normality is not rejected; two-sided Wilcoxon signed-rank when normality is rejected.\n"
                + "- Secondary p-values: Holm-Bonferroni correction.\n"
                + "- Bootstrap: deterministic paired percentile bootstrap, 10000 iterations, seed 20260920.\n"
                + "- Current paired scenarios: " + research.shortestVsEnergyAware().comparableScenarioCount() + ".\n"
                + "- Observed mean predicted energy saving: " + energy.distribution().mean() + " mAh.\n"
                + "- Observed median predicted energy saving: " + energy.distribution().median() + " mAh.\n"
                + "- Observed 95% CI: [" + energy.meanConfidenceInterval().lower() + ", "
                + energy.meanConfidenceInterval().upper() + "] mAh.\n"
                + "- Primary test: " + energy.hypothesisTest().testName() + ", p="
                + energy.hypothesisTest().pValue() + ", effect size="
                + energy.effectSize().value() + " (" + energy.effectSize().type() + ").\n"
                + "- Limitations: energy is predicted, not measured real-flight battery consumption; planning time is wall-clock runtime; planned altitude field is Gazebo World Z.\n";
    }

    private String algorithmSummary(PlanningResearchAnalysis analysis) {
        List<List<String>> rows = new ArrayList<>();
        rows.add(List.of("algorithm", "total_scenarios", "feasible_scenarios", "infeasible_scenarios",
                "feasibility_rate_percent", "distance_mean_m", "distance_median_m", "distance_stddev_m",
                "duration_mean_sec", "duration_median_sec", "duration_stddev_sec",
                "predicted_energy_mean_mah", "predicted_energy_median_mah", "predicted_energy_stddev_mah",
                "predicted_battery_mean_percent", "predicted_battery_median_percent", "predicted_battery_stddev_percent",
                "planning_time_mean_ms", "planning_time_median_ms", "planning_time_stddev_ms"));
        for (AlgorithmDescriptiveStatistics s : analysis.algorithms()) {
            rows.add(row(s.algorithm().name(), Integer.toString(s.totalScenarioCount()),
                    Integer.toString(s.feasibleScenarioCount()), Integer.toString(s.infeasibleScenarioCount()),
                    CsvFormatter.value(s.feasibilityRatePercent()),
                    CsvFormatter.value(s.distanceM().mean()), CsvFormatter.value(s.distanceM().median()),
                    CsvFormatter.value(s.distanceM().standardDeviation()),
                    CsvFormatter.value(s.durationSec().mean()), CsvFormatter.value(s.durationSec().median()),
                    CsvFormatter.value(s.durationSec().standardDeviation()),
                    CsvFormatter.value(s.energyMah().mean()), CsvFormatter.value(s.energyMah().median()),
                    CsvFormatter.value(s.energyMah().standardDeviation()),
                    CsvFormatter.value(s.batteryUsedPercent().mean()), CsvFormatter.value(s.batteryUsedPercent().median()),
                    CsvFormatter.value(s.batteryUsedPercent().standardDeviation()),
                    CsvFormatter.value(s.planningTimeMs().mean()), CsvFormatter.value(s.planningTimeMs().median()),
                    CsvFormatter.value(s.planningTimeMs().standardDeviation())));
        }
        return CsvFormatter.csv(rows);
    }

    private String pairedScenarios(PlanningResearchAnalysis analysis) {
        List<List<String>> rows = pairedHeader();
        for (PairedScenarioObservation o : observations(analysis)) rows.add(pairedRow(o));
        return CsvFormatter.csv(rows);
    }

    private List<List<String>> pairedHeader() {
        List<List<String>> rows = new ArrayList<>();
        rows.add(List.of("scenario_id", "scenario_label", "shortest_distance_m", "energy_aware_distance_m",
                "distance_difference_m", "distance_difference_percent", "shortest_duration_sec",
                "energy_aware_duration_sec", "duration_difference_sec", "duration_difference_percent",
                "shortest_world_z_m", "energy_aware_world_z_m", "world_z_difference_m",
                "shortest_predicted_energy_mah", "energy_aware_predicted_energy_mah", "energy_saving_mah",
                "energy_saving_percent", "shortest_planning_time_ms", "energy_aware_planning_time_ms",
                "planning_time_difference_ms", "energy_difference_approximately_zero"));
        return rows;
    }

    private List<String> pairedRow(PairedScenarioObservation o) {
        return row(o.scenarioId(), o.scenarioLabel(), CsvFormatter.value(o.shortestDistanceM()),
                CsvFormatter.value(o.energyAwareDistanceM()), CsvFormatter.value(o.distanceDifferenceM()),
                CsvFormatter.value(o.distanceDifferencePercent()), CsvFormatter.value(o.shortestDurationSec()),
                CsvFormatter.value(o.energyAwareDurationSec()), CsvFormatter.value(o.durationDifferenceSec()),
                CsvFormatter.value(o.durationDifferencePercent()), CsvFormatter.value(o.shortestWorldZM()),
                CsvFormatter.value(o.energyAwareWorldZM()), CsvFormatter.value(o.altitudeDifferenceM()),
                CsvFormatter.value(o.shortestEnergyMah()), CsvFormatter.value(o.energyAwareEnergyMah()),
                CsvFormatter.value(o.energySavingMah()), CsvFormatter.value(o.energySavingPercent()),
                CsvFormatter.value(o.shortestPlanningTimeMs()), CsvFormatter.value(o.energyAwarePlanningTimeMs()),
                CsvFormatter.value(o.planningTimeDifferenceMs()),
                Boolean.toString(Math.abs(o.energySavingMah()) <= EPSILON));
    }

    private String statisticalSummary(PlanningStatisticalAnalysis statistics) {
        List<List<String>> rows = new ArrayList<>();
        rows.add(List.of("metric", "role", "unit", "sign_convention", "pair_count", "zero_difference_count",
                "effective_test_n", "mean", "median", "sample_stddev", "q1", "q3", "iqr", "min", "max",
                "skewness", "outlier_count", "normality_test", "normality_statistic", "normality_p_value",
                "normality_rejected", "paired_test", "test_statistic", "degrees_of_freedom", "raw_p_value",
                "holm_adjusted_p_value", "h0_rejected", "effect_size_type", "effect_size", "ci_level",
                "ci_lower", "ci_upper", "ci_method"));
        rows.add(statRow(statistics.primaryEnergyAnalysis(), "ENERGY", "PRIMARY"));
        for (MetricStatisticalAnalysis metric : statistics.secondaryMetricAnalyses()) {
            rows.add(statRow(metric, metricName(metric.metricKey()), "SECONDARY"));
        }
        return CsvFormatter.csv(rows);
    }

    private List<String> statRow(MetricStatisticalAnalysis m, String metric, String role) {
        return row(metric, role, m.unit(), m.signConvention(),
                Integer.toString(m.hypothesisTest().originalPairCount()),
                Integer.toString(m.hypothesisTest().zeroDifferenceCount()),
                Integer.toString(m.hypothesisTest().effectiveN()), CsvFormatter.value(m.distribution().mean()),
                CsvFormatter.value(m.distribution().median()), CsvFormatter.value(m.distribution().sampleStandardDeviation()),
                CsvFormatter.value(m.distribution().q1()), CsvFormatter.value(m.distribution().q3()),
                CsvFormatter.value(m.distribution().iqr()), CsvFormatter.value(m.distribution().min()),
                CsvFormatter.value(m.distribution().max()), CsvFormatter.value(m.distribution().skewness()),
                Integer.toString(m.distribution().outliers().outlierCount()), m.normality().method(),
                CsvFormatter.value(m.normality().statistic()), CsvFormatter.value(m.normality().pValue()),
                Boolean.toString(m.normality().rejectNormality()), m.hypothesisTest().testName(),
                CsvFormatter.value(m.hypothesisTest().statistic()), CsvFormatter.value(m.hypothesisTest().degreesOfFreedom()),
                CsvFormatter.value(m.rawPValue()), CsvFormatter.value(m.holmAdjustedPValue()),
                Boolean.toString(m.hypothesisTest().rejectNull()), m.effectSize().type(),
                CsvFormatter.value(m.effectSize().value()), CsvFormatter.value(m.meanConfidenceInterval().confidenceLevel()),
                CsvFormatter.value(m.meanConfidenceInterval().lower()), CsvFormatter.value(m.meanConfidenceInterval().upper()),
                m.meanConfidenceInterval().method());
    }

    private String figureEnergySaving(PlanningResearchAnalysis analysis) {
        List<List<String>> rows = new ArrayList<>();
        rows.add(List.of("scenario_id", "energy_saving_mah", "energy_saving_percent", "approximately_zero"));
        for (PairedScenarioObservation o : observations(analysis)) rows.add(row(o.scenarioId(),
                CsvFormatter.value(o.energySavingMah()), CsvFormatter.value(o.energySavingPercent()),
                Boolean.toString(Math.abs(o.energySavingMah()) <= EPSILON)));
        return CsvFormatter.csv(rows);
    }

    private String figureDistanceEnergy(PlanningResearchAnalysis analysis) {
        List<List<String>> rows = new ArrayList<>();
        rows.add(List.of("scenario_id", "distance_difference_m", "distance_difference_percent",
                "energy_saving_mah", "energy_saving_percent"));
        for (PairedScenarioObservation o : observations(analysis)) rows.add(row(o.scenarioId(),
                CsvFormatter.value(o.distanceDifferenceM()), CsvFormatter.value(o.distanceDifferencePercent()),
                CsvFormatter.value(o.energySavingMah()), CsvFormatter.value(o.energySavingPercent())));
        return CsvFormatter.csv(rows);
    }

    private String figureWorldZ(PlanningResearchAnalysis analysis) {
        List<List<String>> rows = new ArrayList<>();
        rows.add(List.of("scenario_id", "shortest_world_z_m", "energy_aware_world_z_m",
                "world_z_difference_m", "energy_saving_mah", "energy_saving_percent"));
        for (PairedScenarioObservation o : observations(analysis)) rows.add(row(o.scenarioId(),
                CsvFormatter.value(o.shortestWorldZM()), CsvFormatter.value(o.energyAwareWorldZM()),
                CsvFormatter.value(o.altitudeDifferenceM()), CsvFormatter.value(o.energySavingMah()),
                CsvFormatter.value(o.energySavingPercent())));
        return CsvFormatter.csv(rows);
    }

    private String figurePlanningTime(PlanningResearchAnalysis analysis) {
        List<List<String>> rows = new ArrayList<>();
        rows.add(List.of("scenario_id", "shortest_planning_time_ms", "energy_aware_planning_time_ms",
                "planning_time_difference_ms"));
        for (PairedScenarioObservation o : observations(analysis)) rows.add(row(o.scenarioId(),
                CsvFormatter.value(o.shortestPlanningTimeMs()), CsvFormatter.value(o.energyAwarePlanningTimeMs()),
                CsvFormatter.value(o.planningTimeDifferenceMs())));
        return CsvFormatter.csv(rows);
    }

    private String figureEnergyDistribution(PlanningResearchAnalysis analysis) {
        List<List<String>> rows = new ArrayList<>();
        rows.add(List.of("scenario_id", "algorithm", "predicted_energy_mah"));
        for (PairedScenarioObservation o : observations(analysis)) {
            rows.add(row(o.scenarioId(), "ASTAR_SHORTEST", CsvFormatter.value(o.shortestEnergyMah())));
            rows.add(row(o.scenarioId(), "ASTAR_ENERGY_AWARE", CsvFormatter.value(o.energyAwareEnergyMah())));
        }
        return CsvFormatter.csv(rows);
    }

    private String figurePlanningTimeDistribution(PlanningResearchAnalysis analysis) {
        List<List<String>> rows = new ArrayList<>();
        rows.add(List.of("scenario_id", "algorithm", "planning_time_ms"));
        for (PairedScenarioObservation o : observations(analysis)) {
            rows.add(row(o.scenarioId(), "ASTAR_SHORTEST", CsvFormatter.value(o.shortestPlanningTimeMs())));
            rows.add(row(o.scenarioId(), "ASTAR_ENERGY_AWARE", CsvFormatter.value(o.energyAwarePlanningTimeMs())));
        }
        return CsvFormatter.csv(rows);
    }

    private String manifest(PlanningExperimentResult experiment, PlanningResearchAnalysis research,
            PlanningStatisticalAnalysis statistics) {
        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("artifactVersion", "1");
        manifest.put("experimentId", experiment.definition().experimentId());
        manifest.put("generatedArtifactNames", List.of("methodology-summary.md", "algorithm-summary.csv",
                "paired-scenarios.csv", "statistical-summary.csv", "figure-energy-saving.csv",
                "figure-distance-energy-tradeoff.csv", "figure-world-z-difference.csv",
                "figure-planning-time.csv", "figure-energy-distribution.csv",
                "figure-planning-time-distribution.csv", "research-artifact-manifest.json"));
        manifest.put("scenarioCount", experiment.definition().scenarios().size());
        manifest.put("datasetRowCount", experiment.dataset().size());
        manifest.put("pairedScenarioCount", research.shortestVsEnergyAware().comparableScenarioCount());
        manifest.put("algorithms", List.of("DIRECT", "ASTAR_SHORTEST", "ASTAR_ENERGY_AWARE"));
        manifest.put("primaryEndpoint", "predicted mission energy saving, shortestEnergy - energyAwareEnergy");
        manifest.put("alpha", statistics.alpha());
        manifest.put("bootstrapIterations", statistics.bootstrapIterations());
        manifest.put("bootstrapSeed", statistics.bootstrapSeed());
        manifest.put("epsilon", statistics.epsilon());
        manifest.put("coordinateSystem", "LOCAL_SIMULATION_METERS_GAZEBO_XY");
        manifest.put("altitudeSemantics", "GAZEBO_WORLD_Z_METERS");
        manifest.put("energySemantics", "PREDICTED_MISSION_ENERGY_MAH");
        manifest.put("timingSemantics", "WALL_CLOCK_PLANNING_TIME_MS");
        try {
            return objectMapper.writeValueAsString(manifest) + "\n";
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialize research artifact manifest.", e);
        }
    }

    private List<PairedScenarioObservation> observations(PlanningResearchAnalysis analysis) {
        return analysis.shortestVsEnergyAware().observations().stream()
                .sorted(Comparator.comparing(PairedScenarioObservation::scenarioId)).toList();
    }

    private String metricName(String key) {
        return switch (key) {
            case "distanceDifferenceM" -> "DISTANCE";
            case "durationDifferenceSec" -> "DURATION";
            case "altitudeDifferenceM" -> "WORLD_Z";
            case "planningTimeDifferenceMs" -> "PLANNING_TIME";
            default -> key;
        };
    }

    private List<String> row(String... values) {
        return Arrays.asList(values);
    }

    private void verifyConsistency(PlanningResearchAnalysis analysis) {
        for (PairedScenarioObservation o : observations(analysis)) {
            assertClose(o.distanceDifferenceM(), o.energyAwareDistanceM() - o.shortestDistanceM(), o.scenarioId(), "distance");
            assertClose(o.durationDifferenceSec(), o.energyAwareDurationSec() - o.shortestDurationSec(), o.scenarioId(), "duration");
            assertClose(o.altitudeDifferenceM(), o.energyAwareWorldZM() - o.shortestWorldZM(), o.scenarioId(), "worldZ");
            assertClose(o.energySavingMah(), o.shortestEnergyMah() - o.energyAwareEnergyMah(), o.scenarioId(), "energy");
            if (o.planningTimeDifferenceMs() != o.energyAwarePlanningTimeMs() - o.shortestPlanningTimeMs()) {
                throw new IllegalStateException("Planning time mismatch for " + o.scenarioId());
            }
        }
    }

    private void assertClose(Double actual, double expected, String scenario, String field) {
        if (actual == null || Math.abs(actual - expected) > 1.0e-6) {
            throw new IllegalStateException("Paired " + field + " mismatch for " + scenario);
        }
    }
}
