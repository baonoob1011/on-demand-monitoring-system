package com.ondemandmonitoring.planning.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ondemandmonitoring.planning.domain.PlanningGrid;
import com.ondemandmonitoring.planning.dto.PlanningBenchmarkDefinition;
import com.ondemandmonitoring.planning.dto.PlanningBenchmarkGenerationResult;
import com.ondemandmonitoring.planning.dto.PlanningExperimentScenario;
import com.ondemandmonitoring.planning.dto.response.EnvironmentSample;
import com.ondemandmonitoring.planning.service.impl.PlanningBenchmarkScenarioGeneratorImpl;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PlanningBenchmarkScenarioGeneratorTest {

    @Test
    void generatesDeterministicStratifiedScenariosAndUsesOneSnapshot() {
        FakeEnvironment environment = new FakeEnvironment(0.0, 200.0, 0.0, 200.0, 50.0);
        environment.restricted("0.0,0.0");
        environment.noData("50.0,0.0");
        PlanningBenchmarkScenarioGenerator generator = new PlanningBenchmarkScenarioGeneratorImpl(environment);
        PlanningBenchmarkDefinition definition = new PlanningBenchmarkDefinition(
                "BENCH", 100.0, 100.0, 50.0, 8, 50.0, 2, 2);

        PlanningBenchmarkGenerationResult first = generator.generate(definition);
        PlanningBenchmarkGenerationResult second = generator.generate(definition);

        assertThat(first.scenarios()).isEqualTo(second.scenarios());
        assertThat(first.selectedCandidates()).isEqualTo(second.selectedCandidates());
        assertThat(first.scenarios()).hasSize(8);
        assertThat(first.acceptedCount()).isEqualTo(8);
        assertThat(first.validCandidateCount()).isGreaterThanOrEqualTo(8);
        assertThat(first.rejectedRestrictedCount()).isEqualTo(1);
        assertThat(first.rejectedNoDataCount()).isEqualTo(1);
        assertThat(first.rejectedTooCloseToHomeCount()).isEqualTo(1);
        assertThat(first.rejectedOutsideCount()).isZero();
        assertThat(first.occupiedStrataCount()).isEqualTo(4);
        assertThat(environment.snapshotCount()).isEqualTo(2);

        assertThat(first.scenarios()).extracting(PlanningExperimentScenario::scenarioId)
                .containsExactly("B001", "B002", "B003", "B004", "B005", "B006", "B007", "B008");
        assertThat(first.scenarios()).extracting(PlanningExperimentScenario::label)
                .containsExactly("benchmark-target-001", "benchmark-target-002", "benchmark-target-003",
                        "benchmark-target-004", "benchmark-target-005", "benchmark-target-006",
                        "benchmark-target-007", "benchmark-target-008");
        assertThat(new HashSet<>(first.scenarios())).hasSize(8);
        assertThat(first.scenarios().stream().map(s -> s.targetX() + "," + s.targetY()).distinct().count())
                .isEqualTo(8);
        assertThat(first.selectedCandidates()).extracting(candidate -> candidate.stratumIndex())
                .contains(0, 1, 2, 3);
        assertThat(first.scenarios()).noneMatch(s -> s.targetX() == 100.0 && s.targetY() == 100.0);
    }

    @Test
    void failsExplicitlyWhenRequestedCountExceedsAvailableValidCandidates() {
        FakeEnvironment environment = new FakeEnvironment(0.0, 100.0, 0.0, 100.0, 50.0);
        PlanningBenchmarkScenarioGenerator generator = new PlanningBenchmarkScenarioGeneratorImpl(environment);
        PlanningBenchmarkDefinition definition = new PlanningBenchmarkDefinition(
                "BENCH", 50.0, 50.0, 50.0, 100, 0.0, 2, 2);

        assertThatThrownBy(() -> generator.generate(definition))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Requested 100");
    }

    @Test
    void rejectsInvalidDefinitions() {
        PlanningBenchmarkScenarioGenerator generator = new PlanningBenchmarkScenarioGeneratorImpl(
                new FakeEnvironment(0.0, 100.0, 0.0, 100.0, 50.0));
        assertThatThrownBy(() -> generator.generate(new PlanningBenchmarkDefinition(
                "BENCH", 0.0, 0.0, Double.NaN, 1, 0.0, 1, 1)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("spacing");
        assertThatThrownBy(() -> generator.generate(new PlanningBenchmarkDefinition(
                "BENCH", 0.0, 0.0, Double.POSITIVE_INFINITY, 1, 0.0, 1, 1)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("spacing");
        assertThatThrownBy(() -> generator.generate(new PlanningBenchmarkDefinition(
                "BENCH", 0.0, 0.0, 0.0, 1, 0.0, 1, 1)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("spacing");
        assertThatThrownBy(() -> generator.generate(new PlanningBenchmarkDefinition(
                "BENCH", 0.0, 0.0, 50.0, 0, 0.0, 1, 1)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("count");
        assertThatThrownBy(() -> generator.generate(new PlanningBenchmarkDefinition(
                "BENCH", Double.NaN, 0.0, 50.0, 1, 0.0, 1, 1)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("HOME");
        assertThatThrownBy(() -> generator.generate(new PlanningBenchmarkDefinition(
                "BENCH", 0.0, 0.0, 50.0, 1, -1.0, 1, 1)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("distance");
        assertThatThrownBy(() -> generator.generate(new PlanningBenchmarkDefinition(
                "BENCH", 0.0, 0.0, 50.0, 1, 0.0, 0, 1)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("stratum");
    }

    @Test
    void generatorDependsOnlyOnPlanningEnvironment() {
        var constructors = PlanningBenchmarkScenarioGeneratorImpl.class.getConstructors();
        assertThat(constructors).hasSize(1);
        assertThat(constructors[0].getParameterTypes()).containsExactly(PlanningEnvironment.class);
    }

    private static final class FakeEnvironment implements PlanningEnvironment {

        private final PlanningGrid grid;
        private final Set<String> restricted = new HashSet<>();
        private final Set<String> noData = new HashSet<>();
        private int snapshotCount;

        private FakeEnvironment(double minX, double maxX, double minY, double maxY, double resolution) {
            int width = (int) ((maxX - minX) / resolution) + 1;
            int height = (int) ((maxY - minY) / resolution) + 1;
            List<Double> values = new ArrayList<>();
            for (int i = 0; i < width * height; i++) values.add((double) i);
            this.grid = new PlanningGrid("test", "sim",
                    new PlanningGrid.Bounds(minX, maxX, minY, maxY),
                    resolution, width, height, "row-major", values, values, values);
        }

        void restricted(String key) {
            restricted.add(key);
        }

        void noData(String key) {
            noData.add(key);
        }

        int snapshotCount() {
            return snapshotCount;
        }

        @Override
        public EnvironmentSample sample(double simX, double simY) {
            boolean inside = grid.contains(simX, simY);
            String key = simX + "," + simY;
            boolean missing = noData.contains(key);
            Double terrain = inside && !missing ? grid.sample(simX, simY).terrainElevationM() : null;
            Double surface = inside && !missing ? grid.sample(simX, simY).surfaceElevationM() : null;
            return new EnvironmentSample(simX, simY, inside, terrain, null, surface,
                    restricted.contains(key), restricted.contains(key) ? "R" : null,
                    restricted.contains(key) ? "Restricted" : null);
        }

        @Override
        public PlanningGrid grid() {
            return grid;
        }

        @Override
        public PlanningEnvironment snapshot() {
            snapshotCount++;
            return this;
        }
    }
}
