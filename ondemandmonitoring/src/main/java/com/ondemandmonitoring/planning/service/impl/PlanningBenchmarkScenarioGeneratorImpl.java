package com.ondemandmonitoring.planning.service.impl;

import com.ondemandmonitoring.planning.domain.PlanningGrid;
import com.ondemandmonitoring.planning.dto.PlanningBenchmarkCandidate;
import com.ondemandmonitoring.planning.dto.PlanningBenchmarkDefinition;
import com.ondemandmonitoring.planning.dto.PlanningBenchmarkGenerationResult;
import com.ondemandmonitoring.planning.dto.PlanningExperimentScenario;
import com.ondemandmonitoring.planning.dto.response.EnvironmentSample;
import com.ondemandmonitoring.planning.service.PlanningBenchmarkScenarioGenerator;
import com.ondemandmonitoring.planning.service.PlanningEnvironment;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlanningBenchmarkScenarioGeneratorImpl implements PlanningBenchmarkScenarioGenerator {

    private final PlanningEnvironment planningEnvironment;

    public PlanningBenchmarkScenarioGeneratorImpl(PlanningEnvironment planningEnvironment) {
        this.planningEnvironment = planningEnvironment;
    }

    @Override
    @Transactional(readOnly = true)
    public PlanningBenchmarkGenerationResult generate(PlanningBenchmarkDefinition definition) {
        validate(definition);
        long started = System.nanoTime();
        PlanningEnvironment environment = planningEnvironment.snapshot();
        PlanningGrid grid = environment.grid();
        PlanningGrid.Bounds bounds = grid.bounds();
        int candidateCount = 0;
        int outside = 0;
        int noData = 0;
        int restricted = 0;
        int tooClose = 0;

        List<List<PlanningBenchmarkCandidate>> byStratum = new ArrayList<>();
        int totalStrata = definition.stratumColumns() * definition.stratumRows();
        for (int i = 0; i < totalStrata; i++) byStratum.add(new ArrayList<>());

        int rowCount = (int) Math.floor((bounds.maxY() - bounds.minY()) / definition.samplingSpacingM()) + 1;
        int columnCount = (int) Math.floor((bounds.maxX() - bounds.minX()) / definition.samplingSpacingM()) + 1;
        for (int row = 0; row < rowCount; row++) {
            double y = bounds.minY() + row * definition.samplingSpacingM();
            for (int column = 0; column < columnCount; column++) {
                double x = bounds.minX() + column * definition.samplingSpacingM();
                candidateCount++;
                EnvironmentSample sample = environment.sample(x, y);
                if (!sample.insideWorldBounds()) {
                    outside++;
                    continue;
                }
                if (sample.terrainElevationM() == null || sample.surfaceElevationM() == null) {
                    noData++;
                    continue;
                }
                if (sample.restricted()) {
                    restricted++;
                    continue;
                }
                double distance = distance(definition.homeX(), definition.homeY(), x, y);
                if (distance < definition.minimumTargetDistanceFromHomeM()) {
                    tooClose++;
                    continue;
                }
                PlanningBenchmarkCandidate candidate = candidate(definition, bounds, x, y, distance,
                        sample.terrainElevationM(), sample.surfaceElevationM());
                byStratum.get(candidate.stratumIndex()).add(candidate);
            }
        }

        int valid = byStratum.stream().mapToInt(List::size).sum();
        if (valid < definition.targetScenarioCount()) {
            throw new IllegalStateException("Requested " + definition.targetScenarioCount()
                    + " benchmark scenarios but only " + valid + " valid candidates are available.");
        }

        List<PlanningBenchmarkCandidate> selected = selectRoundRobin(byStratum, definition.targetScenarioCount());
        List<PlanningExperimentScenario> scenarios = scenarios(selected);
        int occupied = (int) selected.stream().map(PlanningBenchmarkCandidate::stratumIndex).distinct().count();
        return new PlanningBenchmarkGenerationResult(
                definition,
                scenarios,
                selected,
                candidateCount,
                valid,
                selected.size(),
                outside,
                noData,
                restricted,
                tooClose,
                occupied,
                totalStrata,
                elapsedMs(started));
    }

    private List<PlanningBenchmarkCandidate> selectRoundRobin(
            List<List<PlanningBenchmarkCandidate>> byStratum, int targetCount) {
        List<PlanningBenchmarkCandidate> selected = new ArrayList<>(targetCount);
        int offset = 0;
        while (selected.size() < targetCount) {
            boolean selectedAny = false;
            for (List<PlanningBenchmarkCandidate> stratum : byStratum) {
                if (offset < stratum.size()) {
                    selected.add(stratum.get(offset));
                    selectedAny = true;
                    if (selected.size() == targetCount) break;
                }
            }
            if (!selectedAny) break;
            offset++;
        }
        return selected;
    }

    private List<PlanningExperimentScenario> scenarios(List<PlanningBenchmarkCandidate> selected) {
        List<PlanningExperimentScenario> scenarios = new ArrayList<>(selected.size());
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < selected.size(); i++) {
            PlanningBenchmarkCandidate candidate = selected.get(i);
            String id = "B%03d".formatted(i + 1);
            if (!ids.add(id)) throw new IllegalStateException("Duplicate benchmark scenario ID: " + id);
            scenarios.add(new PlanningExperimentScenario(
                    id,
                    "benchmark-target-%03d".formatted(i + 1),
                    candidate.targetX(),
                    candidate.targetY()));
        }
        return scenarios;
    }

    private PlanningBenchmarkCandidate candidate(
            PlanningBenchmarkDefinition definition,
            PlanningGrid.Bounds bounds,
            double x,
            double y,
            double distance,
            Double terrain,
            Double surface) {
        int column = stratumIndex(x, bounds.minX(), bounds.maxX(), definition.stratumColumns());
        int row = stratumIndex(y, bounds.minY(), bounds.maxY(), definition.stratumRows());
        return new PlanningBenchmarkCandidate(
                x, y, distance, terrain, surface, column, row,
                row * definition.stratumColumns() + column);
    }

    private int stratumIndex(double value, double min, double max, int strata) {
        if (max <= min) return 0;
        double normalized = (value - min) / (max - min);
        int index = (int) Math.floor(normalized * strata);
        return Math.max(0, Math.min(strata - 1, index));
    }

    private void validate(PlanningBenchmarkDefinition definition) {
        if (definition == null) throw new IllegalArgumentException("Benchmark definition is required.");
        if (definition.benchmarkId() == null || definition.benchmarkId().isBlank()) {
            throw new IllegalArgumentException("Benchmark ID is required.");
        }
        if (!Double.isFinite(definition.homeX()) || !Double.isFinite(definition.homeY())) {
            throw new IllegalArgumentException("Benchmark HOME coordinates must be finite.");
        }
        if (!Double.isFinite(definition.samplingSpacingM()) || definition.samplingSpacingM() <= 0.0) {
            throw new IllegalArgumentException("Benchmark sampling spacing must be finite and positive.");
        }
        if (definition.targetScenarioCount() <= 0) {
            throw new IllegalArgumentException("Benchmark target scenario count must be positive.");
        }
        if (!Double.isFinite(definition.minimumTargetDistanceFromHomeM())
                || definition.minimumTargetDistanceFromHomeM() < 0.0) {
            throw new IllegalArgumentException("Benchmark minimum HOME distance must be finite and non-negative.");
        }
        if (definition.stratumColumns() <= 0 || definition.stratumRows() <= 0) {
            throw new IllegalArgumentException("Benchmark stratum dimensions must be positive.");
        }
    }

    private double distance(double ax, double ay, double bx, double by) {
        return Math.hypot(bx - ax, by - ay);
    }

    private long elapsedMs(long started) {
        return Math.max(0L, (System.nanoTime() - started) / 1_000_000L);
    }
}
