package com.ondemandmonitoring.planning.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.ondemandmonitoring.mission.enums.PlanningAlgorithm;
import com.ondemandmonitoring.planning.dto.FeasibilityPairSummary;
import com.ondemandmonitoring.planning.dto.MetricStatisticalAnalysis;
import com.ondemandmonitoring.planning.dto.PairedAlgorithmAnalysis;
import com.ondemandmonitoring.planning.dto.PairedScenarioObservation;
import com.ondemandmonitoring.planning.dto.PlanningExperimentDefinition;
import com.ondemandmonitoring.planning.dto.PlanningExperimentResult;
import com.ondemandmonitoring.planning.dto.PlanningExperimentScenario;
import com.ondemandmonitoring.planning.dto.PlanningExperimentSummary;
import com.ondemandmonitoring.planning.dto.PlanningResearchAnalysis;
import com.ondemandmonitoring.planning.dto.PlanningStatisticalAnalysis;
import com.ondemandmonitoring.planning.service.impl.PlanningStatisticalAnalysisServiceImpl;
import java.util.ArrayList;
import java.util.List;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;

class PlanningStatisticalAnalysisServiceTest {

    private static final Offset<Double> TOL = Offset.offset(1.0e-9);
    private final PlanningStatisticalAnalysisService service = new PlanningStatisticalAnalysisServiceImpl();

    @Test
    void calculatesDistributionStatisticsForKnownPositiveDifferences() {
        PlanningStatisticalAnalysis analysis = analyze(List.of(1.0, 2.0, 3.0, 4.0, 5.0));

        var d = analysis.primaryEnergyAnalysis().distribution();
        assertThat(d.n()).isEqualTo(5);
        assertThat(d.mean()).isCloseTo(3.0, TOL);
        assertThat(d.median()).isCloseTo(3.0, TOL);
        assertThat(d.sampleStandardDeviation()).isCloseTo(Math.sqrt(2.5), TOL);
        assertThat(d.q1()).isCloseTo(1.5, TOL);
        assertThat(d.q3()).isCloseTo(4.5, TOL);
        assertThat(d.iqr()).isCloseTo(3.0, TOL);
        assertThat(d.min()).isCloseTo(1.0, TOL);
        assertThat(d.max()).isCloseTo(5.0, TOL);
        assertThat(d.outliers().outlierCount()).isZero();
        assertThat(analysis.primaryEnergyAnalysis().hypothesisTest().testName()).contains("paired t-test");
        assertThat(analysis.primaryEnergyAnalysis().hypothesisTest().degreesOfFreedom()).isEqualTo(4);
        assertThat(analysis.primaryEnergyAnalysis().hypothesisTest().statistic()).isCloseTo(
                3.0 / (Math.sqrt(2.5) / Math.sqrt(5.0)), TOL);
        assertThat(analysis.primaryEnergyAnalysis().effectSize().type()).isEqualTo("Cohen's dz");
        assertThat(analysis.primaryEnergyAnalysis().effectSize().value()).isCloseTo(3.0 / Math.sqrt(2.5), TOL);
    }

    @Test
    void handlesAllZeroPairsWithoutNanOrInfinity() {
        PlanningStatisticalAnalysis analysis = analyze(List.of(0.0, 0.0, 0.0, 0.0));

        assertThat(analysis.primaryEnergyAnalysis().distribution().mean()).isEqualTo(0.0);
        assertThat(analysis.primaryEnergyAnalysis().hypothesisTest().zeroDifferenceCount()).isEqualTo(4);
        assertThat(analysis.primaryEnergyAnalysis().hypothesisTest().rejectNull()).isFalse();
        assertThat(analysis.primaryEnergyAnalysis().effectSize().value()).isEqualTo(0.0);
        assertThat(analysis.primaryEnergyAnalysis().meanConfidenceInterval().lower()).isEqualTo(0.0);
        assertThat(analysis.primaryEnergyAnalysis().meanConfidenceInterval().upper()).isEqualTo(0.0);
    }

    @Test
    void usesWilcoxonForSkewedDistributionAndExcludesZeroDifferences() {
        PlanningStatisticalAnalysis analysis = analyze(List.of(0.0, 0.0, 1.0, 1.0, 2.0, 100.0));

        assertThat(analysis.primaryEnergyAnalysis().normality().rejectNormality()).isTrue();
        assertThat(analysis.primaryEnergyAnalysis().hypothesisTest().testName()).contains("Wilcoxon");
        assertThat(analysis.primaryEnergyAnalysis().hypothesisTest().originalPairCount()).isEqualTo(6);
        assertThat(analysis.primaryEnergyAnalysis().hypothesisTest().zeroDifferenceCount()).isEqualTo(2);
        assertThat(analysis.primaryEnergyAnalysis().hypothesisTest().effectiveN()).isEqualTo(4);
        assertThat(analysis.primaryEnergyAnalysis().hypothesisTest().tieHandling()).contains("average ranks");
        assertThat(analysis.primaryEnergyAnalysis().effectSize().type()).isEqualTo("rank-biserial correlation");
    }

    @Test
    void bootstrapAndHolmResultsAreDeterministicForSameInput() {
        PlanningStatisticalAnalysis first = analyze(List.of(1.0, -1.0, 0.0, 2.0, 4.0, 8.0, 16.0));
        PlanningStatisticalAnalysis second = analyze(List.of(1.0, -1.0, 0.0, 2.0, 4.0, 8.0, 16.0));

        assertThat(second.primaryEnergyAnalysis()).isEqualTo(first.primaryEnergyAnalysis());
        assertThat(second.energySavingPercentMeanConfidenceInterval())
                .isEqualTo(first.energySavingPercentMeanConfidenceInterval());
        assertThat(second.secondaryMetricAnalyses()).isEqualTo(first.secondaryMetricAnalyses());
        assertThat(first.primaryEnergyAnalysis().meanConfidenceInterval().lower())
                .isLessThanOrEqualTo(first.primaryEnergyAnalysis().distribution().mean());
        assertThat(first.primaryEnergyAnalysis().meanConfidenceInterval().upper())
                .isGreaterThanOrEqualTo(first.primaryEnergyAnalysis().distribution().mean());
        assertThat(first.secondaryMetricAnalyses()).allMatch(metric -> metric.holmAdjustedPValue() != null);
    }

    @Test
    void mixedPositiveAndNegativeDifferencesRemainTwoSided() {
        PlanningStatisticalAnalysis analysis = analyze(List.of(-3.0, -1.0, 0.0, 1.0, 3.0));

        assertThat(analysis.primaryEnergyAnalysis().hypothesisTest().alternative()).isEqualTo("two-sided");
        assertThat(analysis.primaryEnergyAnalysis().distribution().mean()).isCloseTo(0.0, TOL);
        assertThat(analysis.primaryEnergyAnalysis().hypothesisTest().rejectNull()).isFalse();
    }

    private PlanningStatisticalAnalysis analyze(List<Double> energySavings) {
        PlanningResearchAnalysis research = research(energySavings);
        return service.analyze(experiment(energySavings.size()), research);
    }

    private PlanningExperimentResult experiment(int count) {
        List<PlanningExperimentScenario> scenarios = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            scenarios.add(new PlanningExperimentScenario("S%03d".formatted(i), "scenario-" + i, i, i));
        }
        return new PlanningExperimentResult(
                new PlanningExperimentDefinition("EXP", "Synthetic", 0.0, -280.0, scenarios),
                9.4, List.of(), List.of(),
                new PlanningExperimentSummary(List.of(), count, null, null, null, null,
                        null, null, null, 0, 0, 0, 0),
                0L);
    }

    private PlanningResearchAnalysis research(List<Double> energySavings) {
        List<PairedScenarioObservation> observations = new ArrayList<>();
        for (int i = 0; i < energySavings.size(); i++) {
            double saving = energySavings.get(i);
            double shortestEnergy = 100.0;
            double awareEnergy = shortestEnergy - saving;
            double distanceDifference = i % 2 == 0 ? i : -i;
            observations.add(new PairedScenarioObservation(
                    "S%03d".formatted(i + 1), "scenario-" + (i + 1),
                    100.0, 100.0 + distanceDifference, distanceDifference, distanceDifference,
                    20.0, 20.0 - saving, -saving,
                    50.0, 50.0 - saving, -saving, -saving,
                    shortestEnergy, awareEnergy, saving, saving,
                    10L, 20L + i, 10L + i,
                    saving > 1.0e-9, Math.abs(saving) <= 1.0e-9,
                    distanceDifference > 0.0 && saving > 1.0e-9,
                    saving > 1.0e-9));
        }
        PairedAlgorithmAnalysis paired = new PairedAlgorithmAnalysis(
                PlanningAlgorithm.ASTAR_SHORTEST,
                PlanningAlgorithm.ASTAR_ENERGY_AWARE,
                observations.size(), 0, 0, 0, 0, 0,
                null, null, null, null, null, null, null, null, observations);
        return new PlanningResearchAnalysis("EXP", "Synthetic", energySavings.size(), 0,
                List.of(), new FeasibilityPairSummary(energySavings.size(), 0, 0, 0), paired);
    }
}
