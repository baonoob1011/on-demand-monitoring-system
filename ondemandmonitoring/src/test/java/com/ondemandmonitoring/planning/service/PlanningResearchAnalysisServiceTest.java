package com.ondemandmonitoring.planning.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.ondemandmonitoring.mission.enums.FeasibilityStatus;
import com.ondemandmonitoring.mission.enums.PlanningAlgorithm;
import com.ondemandmonitoring.planning.dto.AlgorithmDescriptiveStatistics;
import com.ondemandmonitoring.planning.dto.PairedAlgorithmAnalysis;
import com.ondemandmonitoring.planning.dto.PairedScenarioObservation;
import com.ondemandmonitoring.planning.dto.PlanningExperimentDatasetRow;
import com.ondemandmonitoring.planning.dto.PlanningExperimentDefinition;
import com.ondemandmonitoring.planning.dto.PlanningExperimentResult;
import com.ondemandmonitoring.planning.dto.PlanningExperimentScenario;
import com.ondemandmonitoring.planning.dto.PlanningExperimentSummary;
import com.ondemandmonitoring.planning.dto.PlanningResearchAnalysis;
import com.ondemandmonitoring.planning.service.impl.PlanningResearchAnalysisServiceImpl;
import java.util.List;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;

class PlanningResearchAnalysisServiceTest {

    private static final Offset<Double> TOL = Offset.offset(1.0e-9);
    private final PlanningResearchAnalysisService service = new PlanningResearchAnalysisServiceImpl();

    @Test
    void calculatesPerAlgorithmDescriptiveStatisticsAndExcludesInfeasibleNulls() {
        PlanningResearchAnalysis analysis = service.analyze(result(List.of(
                feasible("S001", PlanningAlgorithm.DIRECT, 10.0, 10.0, 10.0, 10.0, 20L),
                feasible("S001", PlanningAlgorithm.ASTAR_SHORTEST, 100.0, 30.0, 100.0, 100.0, 10L),
                feasible("S001", PlanningAlgorithm.ASTAR_ENERGY_AWARE, 110.0, 20.0, 90.0, 90.0, 15L),
                feasible("S002", PlanningAlgorithm.DIRECT, 20.0, 20.0, 20.0, 20.0, 40L),
                feasible("S002", PlanningAlgorithm.ASTAR_SHORTEST, 200.0, 40.0, 200.0, 200.0, 20L),
                feasible("S002", PlanningAlgorithm.ASTAR_ENERGY_AWARE, 210.0, 35.0, 180.0, 200.0, 35L),
                noRoute("S003", PlanningAlgorithm.DIRECT, 60L),
                feasible("S003", PlanningAlgorithm.ASTAR_SHORTEST, 300.0, 50.0, 300.0, 300.0, 30L),
                noRoute("S003", PlanningAlgorithm.ASTAR_ENERGY_AWARE, 55L),
                noRoute("S004", PlanningAlgorithm.DIRECT, 80L),
                noRoute("S004", PlanningAlgorithm.ASTAR_SHORTEST, 40L),
                feasible("S004", PlanningAlgorithm.ASTAR_ENERGY_AWARE, 0.0, 5.0, 0.0, 0.0, 45L))));

        AlgorithmDescriptiveStatistics shortest = stats(analysis, PlanningAlgorithm.ASTAR_SHORTEST);
        assertThat(shortest.totalScenarioCount()).isEqualTo(4);
        assertThat(shortest.feasibleScenarioCount()).isEqualTo(3);
        assertThat(shortest.infeasibleScenarioCount()).isEqualTo(1);
        assertThat(shortest.feasibilityRatePercent()).isCloseTo(75.0, TOL);
        assertThat(shortest.distanceM().mean()).isCloseTo(200.0, TOL);
        assertThat(shortest.distanceM().median()).isCloseTo(200.0, TOL);
        assertThat(shortest.distanceM().min()).isCloseTo(100.0, TOL);
        assertThat(shortest.distanceM().max()).isCloseTo(300.0, TOL);
        assertThat(shortest.distanceM().standardDeviation()).isCloseTo(100.0, TOL);
        assertThat(shortest.energyMah().count()).isEqualTo(3);
        assertThat(shortest.planningTimeMs().count()).isEqualTo(4);
        assertThat(shortest.planningTimeMs().median()).isCloseTo(25.0, TOL);

        AlgorithmDescriptiveStatistics direct = stats(analysis, PlanningAlgorithm.DIRECT);
        assertThat(direct.distanceM().median()).isCloseTo(15.0, TOL);
        assertThat(direct.energyMah().standardDeviation()).isCloseTo(Math.sqrt(50.0), TOL);
        assertThat(direct.energyMah().count()).isEqualTo(2);
        assertThat(direct.planningTimePopulation()).isEqualTo("all executed algorithm rows");
    }

    @Test
    void buildsScenarioPairedAnalysisWithFormulasCountsEpsilonAndGuards() {
        PlanningResearchAnalysis analysis = service.analyze(result(List.of(
                feasible("S001", PlanningAlgorithm.DIRECT, 1.0, 1.0, 1.0, 1.0, 1L),
                feasible("S001", PlanningAlgorithm.ASTAR_SHORTEST, 100.0, 30.0, 100.0, 100.0, 10L),
                feasible("S001", PlanningAlgorithm.ASTAR_ENERGY_AWARE, 110.0, 20.0, 90.0, 90.0, 15L),
                noRoute("S002", PlanningAlgorithm.DIRECT, 1L),
                feasible("S002", PlanningAlgorithm.ASTAR_SHORTEST, 200.0, 40.0, 200.0, 200.0, 20L),
                feasible("S002", PlanningAlgorithm.ASTAR_ENERGY_AWARE, 210.0, 35.0, 180.0, 200.0 + 5.0e-10, 35L),
                noRoute("S003", PlanningAlgorithm.DIRECT, 1L),
                feasible("S003", PlanningAlgorithm.ASTAR_SHORTEST, 300.0, 50.0, 300.0, 300.0, 30L),
                noRoute("S003", PlanningAlgorithm.ASTAR_ENERGY_AWARE, 55L),
                noRoute("S004", PlanningAlgorithm.DIRECT, 1L),
                noRoute("S004", PlanningAlgorithm.ASTAR_SHORTEST, 40L),
                feasible("S004", PlanningAlgorithm.ASTAR_ENERGY_AWARE, 0.0, 5.0, 0.0, 0.0, 45L))));

        PairedAlgorithmAnalysis paired = analysis.shortestVsEnergyAware();
        assertThat(paired.comparableScenarioCount()).isEqualTo(2);
        assertThat(analysis.shortestVsEnergyAwareFeasibility().bothShortestAndEnergyAwareFeasible()).isEqualTo(2);
        assertThat(analysis.shortestVsEnergyAwareFeasibility().shortestOnlyFeasible()).isEqualTo(1);
        assertThat(analysis.shortestVsEnergyAwareFeasibility().energyAwareOnlyFeasible()).isEqualTo(1);
        assertThat(analysis.shortestVsEnergyAwareFeasibility().neitherFeasible()).isZero();

        PairedScenarioObservation s1 = paired.observations().stream()
                .filter(value -> value.scenarioId().equals("S001")).findFirst().orElseThrow();
        assertThat(s1.energySavingMah()).isCloseTo(10.0, TOL);
        assertThat(s1.energySavingPercent()).isCloseTo(10.0, TOL);
        assertThat(s1.distanceDifferenceM()).isCloseTo(10.0, TOL);
        assertThat(s1.distanceDifferencePercent()).isCloseTo(10.0, TOL);
        assertThat(s1.durationDifferenceSec()).isCloseTo(-10.0, TOL);
        assertThat(s1.durationDifferencePercent()).isCloseTo(-10.0, TOL);
        assertThat(s1.altitudeDifferenceM()).isCloseTo(-10.0, TOL);
        assertThat(s1.planningTimeDifferenceMs()).isEqualTo(5L);
        assertThat(s1.energySaving()).isTrue();
        assertThat(s1.longerButLowerEnergy()).isTrue();
        assertThat(s1.lowerAltitudeAndLowerEnergy()).isTrue();

        PairedScenarioObservation s2 = paired.observations().stream()
                .filter(value -> value.scenarioId().equals("S002")).findFirst().orElseThrow();
        assertThat(s2.approximatelyEqualEnergy()).isTrue();
        assertThat(s2.energySaving()).isFalse();
        assertThat(paired.energySavingScenarioCount()).isEqualTo(1);
        assertThat(paired.equalEnergyScenarioCount()).isEqualTo(1);
        assertThat(paired.higherEnergyScenarioCount()).isZero();
        assertThat(paired.longerButLowerEnergyCount()).isEqualTo(1);
        assertThat(paired.lowerAltitudeAndLowerEnergyCount()).isEqualTo(1);
        assertThat(paired.energySavingMah().mean()).isCloseTo((10.0 - 5.0e-10) / 2.0, TOL);
        assertThat(paired.energySavingPercent().mean()).isCloseTo((10.0 + (-5.0e-10 / 200.0 * 100.0)) / 2.0, TOL);
        assertThat(paired.energySavingMah().median()).isCloseTo((10.0 - 5.0e-10) / 2.0, TOL);
        assertThat(paired.energySavingMah().standardDeviation()).isNotNull();
        assertThat(paired.observations()).noneMatch(value -> value.scenarioId().equals("S003"));
    }

    @Test
    void returnsNullStddevForSingleValueAndNeverReturnsNanOrInfinity() {
        PlanningResearchAnalysis analysis = service.analyze(result(List.of(
                feasible("S001", PlanningAlgorithm.DIRECT, 1.0, 1.0, 1.0, 1.0, 1L),
                feasible("S001", PlanningAlgorithm.ASTAR_SHORTEST, 0.0, 0.0, 0.0, 0.0, 10L),
                feasible("S001", PlanningAlgorithm.ASTAR_ENERGY_AWARE, 0.0, 0.0, 0.0, 0.0, 15L),
                noRoute("S002", PlanningAlgorithm.DIRECT, 1L),
                noRoute("S002", PlanningAlgorithm.ASTAR_SHORTEST, 20L),
                noRoute("S002", PlanningAlgorithm.ASTAR_ENERGY_AWARE, 25L),
                noRoute("S003", PlanningAlgorithm.DIRECT, 1L),
                noRoute("S003", PlanningAlgorithm.ASTAR_SHORTEST, 30L),
                noRoute("S003", PlanningAlgorithm.ASTAR_ENERGY_AWARE, 35L),
                noRoute("S004", PlanningAlgorithm.DIRECT, 1L),
                noRoute("S004", PlanningAlgorithm.ASTAR_SHORTEST, 40L),
                noRoute("S004", PlanningAlgorithm.ASTAR_ENERGY_AWARE, 45L))));

        PairedScenarioObservation only = analysis.shortestVsEnergyAware().observations().get(0);
        assertThat(only.energySavingPercent()).isNull();
        assertThat(only.distanceDifferencePercent()).isNull();
        assertThat(only.durationDifferencePercent()).isNull();
        assertThat(analysis.shortestVsEnergyAware().energySavingMah().standardDeviation()).isNull();
        assertThat(analysis.shortestVsEnergyAware().energySavingPercent().count()).isZero();
        assertFiniteOrNull(only.energySavingPercent());
        assertFiniteOrNull(analysis.shortestVsEnergyAware().energySavingMah().mean());
    }

    private AlgorithmDescriptiveStatistics stats(PlanningResearchAnalysis analysis, PlanningAlgorithm algorithm) {
        return analysis.algorithms().stream().filter(value -> value.algorithm() == algorithm).findFirst().orElseThrow();
    }

    private PlanningExperimentResult result(List<PlanningExperimentDatasetRow> rows) {
        return new PlanningExperimentResult(
                new PlanningExperimentDefinition("EXP", "Research", 0.0, -280.0, List.of(
                        new PlanningExperimentScenario("S001", "one", 1.0, 1.0),
                        new PlanningExperimentScenario("S002", "two", 2.0, 2.0),
                        new PlanningExperimentScenario("S003", "three", 3.0, 3.0),
                        new PlanningExperimentScenario("S004", "four", 4.0, 4.0))),
                9.4, List.of(), rows, new PlanningExperimentSummary(List.of(), 0,
                null, null, null, null, null, null, null, 0, 0, 0, 0), 0L);
    }

    private PlanningExperimentDatasetRow feasible(String scenario, PlanningAlgorithm algorithm,
            double distance, double z, double duration, double energy, long planningMs) {
        return new PlanningExperimentDatasetRow("EXP", scenario, 0.0, -280.0, 1.0, 1.0,
                algorithm, FeasibilityStatus.FEASIBLE, distance, z, duration,
                energy, energy / 50.0, null, planningMs, 2, null);
    }

    private PlanningExperimentDatasetRow noRoute(String scenario, PlanningAlgorithm algorithm, long planningMs) {
        return new PlanningExperimentDatasetRow("EXP", scenario, 0.0, -280.0, 1.0, 1.0,
                algorithm, FeasibilityStatus.NO_SAFE_ROUTE, null, null, null,
                null, null, null, planningMs, 0, "blocked");
    }

    private void assertFiniteOrNull(Double value) {
        assertThat(value == null || Double.isFinite(value)).isTrue();
    }
}
