package com.ondemandmonitoring.planning.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.ondemandmonitoring.mission.enums.FeasibilityStatus;
import com.ondemandmonitoring.mission.enums.PlanningAlgorithm;
import com.ondemandmonitoring.planning.dto.AlgorithmExperimentSummary;
import com.ondemandmonitoring.planning.dto.AlgorithmPlanningResult;
import com.ondemandmonitoring.planning.dto.PlanningComparisonInput;
import com.ondemandmonitoring.planning.dto.PlanningComparisonResult;
import com.ondemandmonitoring.planning.dto.PlanningExperimentDefinition;
import com.ondemandmonitoring.planning.dto.PlanningExperimentResult;
import com.ondemandmonitoring.planning.dto.PlanningExperimentScenario;
import com.ondemandmonitoring.planning.dto.PlanningTradeoff;
import com.ondemandmonitoring.planning.dto.response.EnvironmentSample;
import com.ondemandmonitoring.planning.service.impl.PlanningExperimentRunnerImpl;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class PlanningExperimentRunnerTest {

    private final PlanningComparisonService comparison = Mockito.mock(PlanningComparisonService.class);
    private final PlanningEnvironment environment = Mockito.mock(PlanningEnvironment.class);
    private final PlanningExperimentRunner runner = new PlanningExperimentRunnerImpl(comparison, environment);

    @Test
    void createsThreeOrderedRowsPerScenarioAndPreservesScenarioOrder() {
        arrangeHome();
        when(comparison.compare(Mockito.any(PlanningComparisonInput.class))).thenAnswer(invocation -> {
            PlanningComparisonInput input = invocation.getArgument(0);
            return feasible(input.contextId(), input.homeX(), input.homeY(), input.targetX(), input.targetY(),
                    100.0, 120.0, 80.0, 110.0, 20.0, 15.0);
        });
        PlanningExperimentDefinition definition = definition(List.of(
                scenario("S001", 10.0, 20.0), scenario("S002", 30.0, 40.0)));

        PlanningExperimentResult result = runner.run(definition);

        assertThat(result.scenarioResults()).extracting(value -> value.scenario().scenarioId())
                .containsExactly("S001", "S002");
        assertThat(result.dataset()).hasSize(6);
        assertThat(result.dataset()).extracting(value -> value.algorithm()).containsExactly(
                PlanningAlgorithm.DIRECT, PlanningAlgorithm.ASTAR_SHORTEST, PlanningAlgorithm.ASTAR_ENERGY_AWARE,
                PlanningAlgorithm.DIRECT, PlanningAlgorithm.ASTAR_SHORTEST, PlanningAlgorithm.ASTAR_ENERGY_AWARE);
        assertThat(result.dataset()).extracting(value -> value.scenarioId())
                .containsExactly("S001", "S001", "S001", "S002", "S002", "S002");
    }

    @Test
    void excludesInfeasibleMetricsFromMeansAndCountsResearchPatterns() {
        arrangeHome();
        when(comparison.compare(Mockito.<PlanningComparisonInput>argThat(
                input -> input != null && input.contextId().equals("S001"))))
                .thenReturn(feasible("S001", 0.0, -280.0, 200.0, -280.0,
                        204.0, 200.0, 188.0, 220.0, 34.0, 19.5));
        when(comparison.compare(Mockito.<PlanningComparisonInput>argThat(
                input -> input != null && input.contextId().equals("S002"))))
                .thenReturn(allInfeasible("S002"));

        PlanningExperimentResult result = runner.run(definition(List.of(
                scenario("S001", 200.0, -280.0), scenario("S002", -200.0, -280.0))));

        AlgorithmExperimentSummary aware = result.summary().algorithms().get(2);
        assertThat(aware.totalScenarios()).isEqualTo(2);
        assertThat(aware.feasibleScenarios()).isEqualTo(1);
        assertThat(aware.infeasibleScenarios()).isEqualTo(1);
        assertThat(aware.feasibilityRatePercent()).isEqualTo(50.0);
        assertThat(aware.meanEnergyMah()).isEqualTo(188.0);
        assertThat(result.summary().comparisonScenarioCount()).isEqualTo(1);
        assertThat(result.summary().meanEnergySavingMah()).isEqualTo(16.0);
        assertThat(result.summary().energySavingScenarioCount()).isEqualTo(1);
        assertThat(result.summary().longerButLowerEnergyCount()).isEqualTo(1);
        assertThat(result.summary().lowerAltitudeAndLowerEnergyCount()).isEqualTo(1);
        assertThat(result.dataset().subList(3, 6)).allMatch(row ->
                row.feasibilityStatus() == FeasibilityStatus.NO_SAFE_ROUTE
                        && row.estimatedEnergyMah() == null
                        && row.plannedDistanceM() == null);
    }

    @Test
    void rejectsEmptyDuplicateAndNonFiniteInputs() {
        assertThatThrownBy(() -> runner.run(definition(List.of())))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("at least one");
        assertThatThrownBy(() -> runner.run(definition(List.of(
                scenario("S001", 1.0, 1.0), scenario("S001", 2.0, 2.0)))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Duplicate");
        assertThatThrownBy(() -> runner.run(definition(List.of(scenario("S001", Double.NaN, 1.0)))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("finite");
        assertThatThrownBy(() -> runner.run(new PlanningExperimentDefinition(
                "EXP", "test", Double.POSITIVE_INFINITY, -280.0,
                List.of(scenario("S001", 1.0, 1.0)))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("finite");
    }

    private void arrangeHome() {
        when(environment.sample(0.0, -280.0)).thenReturn(
                new EnvironmentSample(0.0, -280.0, true, 1.0, 8.4, 9.4, false, null, null));
    }

    private PlanningExperimentDefinition definition(List<PlanningExperimentScenario> scenarios) {
        return new PlanningExperimentDefinition("EXP-1", "test", 0.0, -280.0, scenarios);
    }

    private PlanningExperimentScenario scenario(String id, double x, double y) {
        return new PlanningExperimentScenario(id, id, x, y);
    }

    private PlanningComparisonResult feasible(String id, double homeX, double homeY, double targetX, double targetY,
            double shortestEnergy, double shortestDistance, double awareEnergy, double awareDistance,
            double shortestZ, double awareZ) {
        AlgorithmPlanningResult direct = result(PlanningAlgorithm.DIRECT, 200.0, 200.0, 34.0, 100L);
        AlgorithmPlanningResult shortest = result(
                PlanningAlgorithm.ASTAR_SHORTEST, shortestEnergy, shortestDistance, shortestZ, 50L);
        AlgorithmPlanningResult aware = result(
                PlanningAlgorithm.ASTAR_ENERGY_AWARE, awareEnergy, awareDistance, awareZ, 80L);
        return new PlanningComparisonResult(id, null, homeX, homeY, targetX, targetY, 9.4,
                List.of(direct, shortest, aware), new PlanningTradeoff(
                        shortestEnergy - awareEnergy, (shortestEnergy - awareEnergy) / shortestEnergy * 100.0,
                        awareDistance - shortestDistance, (awareDistance - shortestDistance) / shortestDistance * 100.0,
                        -5.0, -4.0, awareZ - shortestZ, 30L), 230L);
    }

    private PlanningComparisonResult allInfeasible(String id) {
        List<AlgorithmPlanningResult> results = java.util.Arrays.stream(PlanningAlgorithm.values())
                .map(algorithm -> new AlgorithmPlanningResult(algorithm, FeasibilityStatus.NO_SAFE_ROUTE,
                        null, null, null, null, null, null, null, null, null, 1L, 0, "blocked"))
                .toList();
        return new PlanningComparisonResult(id, null, 0.0, -280.0, -200.0, -280.0, 9.4,
                results, new PlanningTradeoff(null, null, null, null, null, null, null, 0L), 3L);
    }

    private AlgorithmPlanningResult result(
            PlanningAlgorithm algorithm, double energy, double distance, double z, long time) {
        return new AlgorithmPlanningResult(algorithm, FeasibilityStatus.FEASIBLE, distance, z, 100.0, 2.0,
                energy, energy / 50.0, null, 20.0, 20.0 + energy / 50.0, time, 2, null);
    }
}
