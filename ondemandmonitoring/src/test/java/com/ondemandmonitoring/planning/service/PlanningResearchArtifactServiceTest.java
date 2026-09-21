package com.ondemandmonitoring.planning.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.ondemandmonitoring.mission.enums.PlanningAlgorithm;
import com.ondemandmonitoring.planning.dto.*;
import com.ondemandmonitoring.planning.service.impl.PlanningResearchArtifactServiceImpl;
import java.util.List;
import org.junit.jupiter.api.Test;

class PlanningResearchArtifactServiceTest {

    private final PlanningResearchArtifactService service = new PlanningResearchArtifactServiceImpl();

    @Test
    void buildsDeterministicArtifactsWithExactHeadersPrecisionEscapingAndZeroFlag() {
        Inputs inputs = inputs(-8.526512829121202E-13, "terrain, energy \"tradeoff\"\nline");

        PlanningResearchArtifactBundle first = service.build(inputs.experiment(), inputs.research(), inputs.statistics());
        PlanningResearchArtifactBundle second = service.build(inputs.experiment(), inputs.research(), inputs.statistics());

        assertThat(second).isEqualTo(first);
        assertThat(first.algorithmSummaryCsv()).startsWith("algorithm,total_scenarios,feasible_scenarios");
        assertThat(first.pairedScenarioCsv()).contains("\"terrain, energy \"\"tradeoff\"\"\nline\"");
        assertThat(first.pairedScenarioCsv()).contains("-8.526512829121202E-13");
        List<List<String>> paired = PlanningExperimentCsvExporterTest.parse(first.pairedScenarioCsv());
        assertThat(paired).hasSize(2);
        assertThat(paired.get(1).get(4)).isEqualTo("10.0");
        assertThat(paired.get(1).get(8)).isEqualTo("-5.0");
        assertThat(paired.get(1).get(12)).isEqualTo("-10.0");
        assertThat(paired.get(1).get(15)).isEqualTo("-8.526512829121202E-13");
        assertThat(paired.get(1).get(19)).isEqualTo("20");
        assertThat(paired.get(1).get(20)).isEqualTo("true");
        assertThat(first.statisticalSummaryCsv()).contains("ENERGY,PRIMARY,mAh");
        assertThat(first.manifestJson()).contains("\"scenarioCount\" : 1");
    }

    @Test
    void flagsNonZeroTinyButMeaningfulEnergyDifferenceAsFalse() {
        PlanningResearchArtifactBundle bundle = service.build(
                inputs(1.0E-6, "plain").experiment(),
                inputs(1.0E-6, "plain").research(),
                inputs(1.0E-6, "plain").statistics());

        assertThat(PlanningExperimentCsvExporterTest.parse(bundle.figureEnergySavingCsv()).get(1).get(3))
                .isEqualTo("false");
    }

    private Inputs inputs(double saving, String label) {
        PairedScenarioObservation obs = new PairedScenarioObservation("B001", label,
                100.0, 110.0, 10.0, 10.0,
                30.0, 20.0, -10.0,
                60.0, 55.0, -5.0, -8.333333333333334,
                200.0, 200.0 - saving, saving, saving / 200.0 * 100.0,
                20L, 40L, 20L, saving > 1.0e-9,
                Math.abs(saving) <= 1.0e-9, saving > 1.0e-9, saving > 1.0e-9);
        PairedAlgorithmAnalysis paired = new PairedAlgorithmAnalysis(
                PlanningAlgorithm.ASTAR_SHORTEST, PlanningAlgorithm.ASTAR_ENERGY_AWARE, 1,
                saving > 1.0e-9 ? 1 : 0, Math.abs(saving) <= 1.0e-9 ? 1 : 0, 0, 0, 0,
                null, null, null, null, null, null, null, null, List.of(obs));
        PlanningResearchAnalysis research = new PlanningResearchAnalysis("EXP", "Synthetic", 1, 3,
                List.of(alg(PlanningAlgorithm.DIRECT), alg(PlanningAlgorithm.ASTAR_SHORTEST),
                        alg(PlanningAlgorithm.ASTAR_ENERGY_AWARE)),
                new FeasibilityPairSummary(1, 0, 0, 0), paired);
        PlanningExperimentResult experiment = new PlanningExperimentResult(
                new PlanningExperimentDefinition("EXP", "Synthetic", 0.0, -280.0,
                        List.of(new PlanningExperimentScenario("B001", label, 1.0, 1.0))),
                9.4, List.of(), List.of(new PlanningExperimentDatasetRow("EXP", "B001", 0.0, -280.0, 1.0, 1.0,
                PlanningAlgorithm.ASTAR_SHORTEST, com.ondemandmonitoring.mission.enums.FeasibilityStatus.FEASIBLE,
                100.0, 30.0, 60.0, 200.0, 4.0, null, 20L, 2, null)),
                new PlanningExperimentSummary(List.of(), 1, null, null, null, null, null, null, null, 0, 0, 0, 0), 0L);
        PlanningStatisticalAnalysis stats = new PlanningStatisticalAnalysis("EXP", 1, 1, 0.05, 1.0e-9,
                "Jarque-Bera", "rule", 10000, 20260920L, metric("energySavingMah", "mAh", true, saving),
                new ConfidenceInterval(0.95, 0.0, 1.0, "bootstrap", 10000, 20260920L),
                List.of(metric("distanceDifferenceM", "m", false, 10.0),
                        metric("durationDifferenceSec", "sec", false, -5.0),
                        metric("altitudeDifferenceM", "m", false, -10.0),
                        metric("planningTimeDifferenceMs", "ms", false, 20.0)),
                new PlanningTimeDistributionComparison(dist(20.0), dist(40.0), dist(20.0)), 1L);
        return new Inputs(experiment, research, stats);
    }

    private AlgorithmDescriptiveStatistics alg(PlanningAlgorithm algorithm) {
        return new AlgorithmDescriptiveStatistics(algorithm, 1, 1, 0, 100.0,
                stats(100.0), stats(60.0), stats(200.12345678901234), stats(4.0), stats(20.0), "all");
    }

    private MetricStatisticalAnalysis metric(String key, String unit, boolean primary, double value) {
        return new MetricStatisticalAnalysis(key, key, unit, "sign", primary, dist(value),
                new NormalityAssessment("Jarque-Bera", 1.0, 0.5, 0.05, false),
                new PairedHypothesisTestResult("test", "two-sided", 1, Math.abs(value) <= 1.0e-9 ? 1 : 0,
                        Math.abs(value) <= 1.0e-9 ? 0 : 1, 1.0, null, 0.01, 0.05, true, "zeros", "ties"),
                new EffectSizeResult("effect", 1.0), new ConfidenceInterval(0.95, value - 1, value + 1,
                "bootstrap", 10000, 20260920L), 0.01, primary ? null : 0.02, !primary);
    }

    private NumericDescriptiveStatistics stats(double value) {
        return new NumericDescriptiveStatistics(1, value, value, value, value, null);
    }

    private MetricDistributionSummary dist(double value) {
        return new MetricDistributionSummary(1, value, value, null, value, value, 0.0,
                value, value, null, null, new OutlierSummary(value, value, 0), List.of(value));
    }

    private record Inputs(
            PlanningExperimentResult experiment,
            PlanningResearchAnalysis research,
            PlanningStatisticalAnalysis statistics) {
    }
}
