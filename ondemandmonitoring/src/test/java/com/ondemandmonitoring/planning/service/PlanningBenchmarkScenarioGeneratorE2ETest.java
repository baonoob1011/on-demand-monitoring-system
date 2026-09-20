package com.ondemandmonitoring.planning.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.ondemandmonitoring.planning.dto.PlanningBenchmarkCandidate;
import com.ondemandmonitoring.planning.dto.PlanningBenchmarkDefinition;
import com.ondemandmonitoring.planning.dto.PlanningBenchmarkGenerationResult;
import com.ondemandmonitoring.planning.dto.PlanningExperimentScenario;
import com.ondemandmonitoring.planning.dto.response.EnvironmentSample;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
@ActiveProfiles("e2e")
class PlanningBenchmarkScenarioGeneratorE2ETest {

    @Autowired private PlanningBenchmarkScenarioGenerator generator;
    @Autowired private PlanningEnvironment environment;

    @Test
    void generatesReproducibleRealBenchmarkWithBroadCoverage() {
        PlanningBenchmarkDefinition definition = definition();

        PlanningBenchmarkGenerationResult first = generator.generate(definition);
        PlanningBenchmarkGenerationResult second = generator.generate(definition);

        assertThat(first.scenarios()).isEqualTo(second.scenarios());
        assertThat(first.selectedCandidates()).isEqualTo(second.selectedCandidates());
        assertThat(first.scenarios()).hasSize(75);
        assertThat(first.acceptedCount()).isEqualTo(75);
        assertThat(first.totalStrataCount()).isEqualTo(25);
        assertThat(first.occupiedStrataCount()).isGreaterThanOrEqualTo(20);
        assertThat(first.validCandidateCount()).isGreaterThanOrEqualTo(75);
        assertThat(first.scenarios()).extracting(PlanningExperimentScenario::scenarioId)
                .containsExactlyElementsOf(java.util.stream.IntStream.rangeClosed(1, 75)
                        .mapToObj(i -> "B%03d".formatted(i)).toList());
        assertThat(first.scenarios()).extracting(PlanningExperimentScenario::label)
                .allMatch(label -> label.startsWith("benchmark-target-"));

        Set<String> ids = new HashSet<>();
        Set<String> coordinates = new HashSet<>();
        for (PlanningExperimentScenario scenario : first.scenarios()) {
            assertThat(ids.add(scenario.scenarioId())).isTrue();
            assertThat(coordinates.add(scenario.targetX() + "," + scenario.targetY())).isTrue();
            EnvironmentSample sample = environment.sample(scenario.targetX(), scenario.targetY());
            assertThat(sample.insideWorldBounds()).as(scenario.scenarioId()).isTrue();
            assertThat(sample.terrainElevationM()).as(scenario.scenarioId()).isNotNull();
            assertThat(sample.surfaceElevationM()).as(scenario.scenarioId()).isNotNull();
            assertThat(sample.restricted()).as(scenario.scenarioId()).isFalse();
            assertThat(Math.hypot(scenario.targetX() - definition.homeX(), scenario.targetY() - definition.homeY()))
                    .isGreaterThanOrEqualTo(definition.minimumTargetDistanceFromHomeM());
        }
        print(first);
    }

    private PlanningBenchmarkDefinition definition() {
        return new PlanningBenchmarkDefinition("BENCHMARK_REAL_V1", 0.0, -280.0, 50.0, 75, 50.0, 5, 5);
    }

    private void print(PlanningBenchmarkGenerationResult result) {
        List<PlanningBenchmarkCandidate> candidates = result.selectedCandidates();
        System.out.printf("BENCHMARK_GENERATION_E2E|candidates=%d|valid=%d|selected=%d|outside=%d|noData=%d|restricted=%d|tooClose=%d|occupiedStrata=%d|totalStrata=%d|generationMs=%d%n",
                result.candidateCount(), result.validCandidateCount(), result.acceptedCount(),
                result.rejectedOutsideCount(), result.rejectedNoDataCount(), result.rejectedRestrictedCount(),
                result.rejectedTooCloseToHomeCount(), result.occupiedStrataCount(), result.totalStrataCount(),
                result.generationElapsedMs());
        System.out.printf("BENCHMARK_GENERATION_E2E|COVERAGE|xMin=%s|xMedian=%s|xMax=%s|yMin=%s|yMedian=%s|yMax=%s|distanceMin=%s|distanceMedian=%s|distanceMax=%s|terrainMin=%s|terrainMedian=%s|terrainMax=%s|surfaceMin=%s|surfaceMedian=%s|surfaceMax=%s%n",
                min(candidates, PlanningBenchmarkCandidate::targetX),
                median(candidates, PlanningBenchmarkCandidate::targetX),
                max(candidates, PlanningBenchmarkCandidate::targetX),
                min(candidates, PlanningBenchmarkCandidate::targetY),
                median(candidates, PlanningBenchmarkCandidate::targetY),
                max(candidates, PlanningBenchmarkCandidate::targetY),
                min(candidates, PlanningBenchmarkCandidate::distanceFromHomeM),
                median(candidates, PlanningBenchmarkCandidate::distanceFromHomeM),
                max(candidates, PlanningBenchmarkCandidate::distanceFromHomeM),
                min(candidates, PlanningBenchmarkCandidate::terrainElevationM),
                median(candidates, PlanningBenchmarkCandidate::terrainElevationM),
                max(candidates, PlanningBenchmarkCandidate::terrainElevationM),
                min(candidates, PlanningBenchmarkCandidate::surfaceElevationM),
                median(candidates, PlanningBenchmarkCandidate::surfaceElevationM),
                max(candidates, PlanningBenchmarkCandidate::surfaceElevationM));
        for (int i = 0; i < Math.min(10, candidates.size()); i++) {
            PlanningExperimentScenario scenario = result.scenarios().get(i);
            PlanningBenchmarkCandidate candidate = candidates.get(i);
            System.out.printf("BENCHMARK_GENERATION_E2E|SAMPLE|id=%s|x=%s|y=%s|distance=%s|terrain=%s|surface=%s|stratum=%d%n",
                    scenario.scenarioId(), scenario.targetX(), scenario.targetY(),
                    candidate.distanceFromHomeM(), candidate.terrainElevationM(),
                    candidate.surfaceElevationM(), candidate.stratumIndex());
        }
    }

    private double min(List<PlanningBenchmarkCandidate> rows,
            java.util.function.Function<PlanningBenchmarkCandidate, Double> getter) {
        return rows.stream().map(getter).mapToDouble(Double::doubleValue).min().orElseThrow();
    }

    private double max(List<PlanningBenchmarkCandidate> rows,
            java.util.function.Function<PlanningBenchmarkCandidate, Double> getter) {
        return rows.stream().map(getter).mapToDouble(Double::doubleValue).max().orElseThrow();
    }

    private double median(List<PlanningBenchmarkCandidate> rows,
            java.util.function.Function<PlanningBenchmarkCandidate, Double> getter) {
        List<Double> sorted = rows.stream().map(getter).sorted().toList();
        int size = sorted.size();
        return size % 2 == 1 ? sorted.get(size / 2) : (sorted.get(size / 2 - 1) + sorted.get(size / 2)) / 2.0;
    }
}
