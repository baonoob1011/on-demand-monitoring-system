package com.ondemandmonitoring.planning.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.ondemandmonitoring.mission.domain.Mission;
import com.ondemandmonitoring.mission.domain.MissionPlan;
import com.ondemandmonitoring.mission.domain.PlanWaypoint;
import com.ondemandmonitoring.mission.enums.MissionStatus;
import com.ondemandmonitoring.mission.enums.PlanningAlgorithm;
import com.ondemandmonitoring.mission.repository.MissionPlanRepository;
import com.ondemandmonitoring.mission.repository.MissionRepository;
import com.ondemandmonitoring.order.domain.Order;
import com.ondemandmonitoring.order.repository.OrderRepository;
import com.ondemandmonitoring.planning.dto.AlgorithmDescriptiveStatistics;
import com.ondemandmonitoring.planning.dto.NumericDescriptiveStatistics;
import com.ondemandmonitoring.planning.dto.PairedAlgorithmAnalysis;
import com.ondemandmonitoring.planning.dto.PlanningBenchmarkDefinition;
import com.ondemandmonitoring.planning.dto.PlanningBenchmarkGenerationResult;
import com.ondemandmonitoring.planning.dto.PlanningExperimentDatasetRow;
import com.ondemandmonitoring.planning.dto.PlanningExperimentDefinition;
import com.ondemandmonitoring.planning.dto.PlanningExperimentResult;
import com.ondemandmonitoring.planning.dto.PlanningResearchAnalysis;
import jakarta.persistence.EntityManager;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
@ActiveProfiles("e2e")
class PlanningResearchBenchmarkE2ETest {

    private static final GeometryFactory GEOMETRY = new GeometryFactory(new PrecisionModel(), 4326);

    @Autowired private PlanningBenchmarkScenarioGenerator generator;
    @Autowired private PlanningExperimentRunner runner;
    @Autowired private PlanningExperimentCsvExporter csvExporter;
    @Autowired private PlanningResearchAnalysisService analysisService;
    @Autowired private MissionPlanningService missionPlanningService;
    @Autowired private MissionRepository missionRepository;
    @Autowired private MissionPlanRepository missionPlanRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private EntityManager entityManager;

    @Test
    void runsFullDeterministicBenchmarkThroughExperimentCsvAndAnalysisWithoutOperationalMutation() {
        MissionPlan operational = createOperationalPlan();
        long missionsBefore = missionRepository.count();
        long plansBefore = missionPlanRepository.count();
        entityManager.flush();
        entityManager.clear();
        PlanSnapshot before = snapshot(missionPlanRepository.findById(operational.getId()).orElseThrow());

        long generationStart = System.nanoTime();
        PlanningBenchmarkGenerationResult generated = generator.generate(benchmarkDefinition());
        long generationMs = elapsed(generationStart);
        PlanningExperimentDefinition experiment = new PlanningExperimentDefinition(
                generated.definition().benchmarkId(),
                "Deterministic generated planning benchmark",
                generated.definition().homeX(),
                generated.definition().homeY(),
                generated.scenarios());

        long experimentStart = System.nanoTime();
        PlanningExperimentResult result = runner.run(experiment);
        long experimentMs = elapsed(experimentStart);
        long csvStart = System.nanoTime();
        String csv = csvExporter.export(result);
        long csvMs = elapsed(csvStart);
        long analysisStart = System.nanoTime();
        PlanningResearchAnalysis analysis = analysisService.analyze(result);
        long analysisMs = elapsed(analysisStart);

        assertThat(generated.scenarios()).hasSize(75);
        assertThat(result.dataset()).hasSize(225);
        assertThat(PlanningExperimentCsvExporterTest.parse(csv)).hasSize(226);
        Set<String> keys = new HashSet<>();
        for (PlanningExperimentDatasetRow row : result.dataset()) {
            assertThat(keys.add(row.experimentId() + "|" + row.scenarioId() + "|" + row.algorithm())).isTrue();
        }
        assertThat(keys).hasSize(225);
        assertThat(analysis.datasetRowCount()).isEqualTo(225);
        assertThat(analysis.shortestVsEnergyAware().comparableScenarioCount()).isLessThanOrEqualTo(75);

        entityManager.flush();
        entityManager.clear();
        PlanSnapshot after = snapshot(missionPlanRepository.findById(before.id()).orElseThrow());
        assertThat(after).isEqualTo(before);
        assertThat(missionRepository.count()).isEqualTo(missionsBefore);
        assertThat(missionPlanRepository.count()).isEqualTo(plansBefore);
        print(generated, result, analysis, generationMs, experimentMs, csvMs, analysisMs, after.equals(before));
    }

    private PlanningBenchmarkDefinition benchmarkDefinition() {
        return new PlanningBenchmarkDefinition("BENCHMARK_REAL_V1", 0.0, -280.0, 50.0, 75, 50.0, 5, 5);
    }

    private void print(PlanningBenchmarkGenerationResult generated, PlanningExperimentResult result,
            PlanningResearchAnalysis analysis, long generationMs, long experimentMs, long csvMs,
            long analysisMs, boolean unchanged) {
        System.out.printf("BENCHMARK_FULL_E2E|CONFIG|benchmark=%s|homeX=%s|homeY=%s|spacing=%s|minDistance=%s|requested=%d|strata=%dx%d%n",
                generated.definition().benchmarkId(), generated.definition().homeX(), generated.definition().homeY(),
                generated.definition().samplingSpacingM(), generated.definition().minimumTargetDistanceFromHomeM(),
                generated.definition().targetScenarioCount(), generated.definition().stratumColumns(),
                generated.definition().stratumRows());
        System.out.printf("BENCHMARK_FULL_E2E|COUNTS|scenarios=%d|algorithms=%d|executions=%d|datasetRows=%d|csvDataRecords=%d|uniqueKeys=%d|candidateCount=%d|valid=%d|outside=%d|noData=%d|restricted=%d|tooClose=%d|occupiedStrata=%d|totalStrata=%d|readOnly=%s%n",
                generated.scenarios().size(), PlanningAlgorithm.values().length, result.dataset().size(),
                result.dataset().size(), result.dataset().size(), result.dataset().size(),
                generated.candidateCount(), generated.validCandidateCount(), generated.rejectedOutsideCount(),
                generated.rejectedNoDataCount(), generated.rejectedRestrictedCount(),
                generated.rejectedTooCloseToHomeCount(), generated.occupiedStrataCount(),
                generated.totalStrataCount(), unchanged);
        for (AlgorithmDescriptiveStatistics stats : analysis.algorithms()) {
            System.out.printf("BENCHMARK_FULL_E2E|ALGORITHM|algorithm=%s|total=%d|feasible=%d|infeasible=%d|rate=%s|distance=%s|duration=%s|energy=%s|battery=%s|planning=%s%n",
                    stats.algorithm(), stats.totalScenarioCount(), stats.feasibleScenarioCount(),
                    stats.infeasibleScenarioCount(), stats.feasibilityRatePercent(),
                    compact(stats.distanceM()), compact(stats.durationSec()), compact(stats.energyMah()),
                    compact(stats.batteryUsedPercent()), compact(stats.planningTimeMs()));
        }
        PairedAlgorithmAnalysis paired = analysis.shortestVsEnergyAware();
        System.out.printf("BENCHMARK_FULL_E2E|PAIRED|count=%d|saving=%d|equal=%d|higher=%d|longerLower=%d|lowerAltitudeLower=%d|energyMah=%s|energyPercent=%s|distanceM=%s|distancePercent=%s|durationSec=%s|durationPercent=%s|altitudeM=%s|planningMs=%s|generationMs=%d|experimentMs=%d|csvMs=%d|analysisMs=%d%n",
                paired.comparableScenarioCount(), paired.energySavingScenarioCount(),
                paired.equalEnergyScenarioCount(), paired.higherEnergyScenarioCount(),
                paired.longerButLowerEnergyCount(), paired.lowerAltitudeAndLowerEnergyCount(),
                compact(paired.energySavingMah()), compact(paired.energySavingPercent()),
                compact(paired.distanceDifferenceM()), compact(paired.distanceDifferencePercent()),
                compact(paired.durationDifferenceSec()), compact(paired.durationDifferencePercent()),
                compact(paired.altitudeDifferenceM()), compact(paired.planningTimeDifferenceMs()),
                generationMs, experimentMs, csvMs, analysisMs);
    }

    private String compact(NumericDescriptiveStatistics stats) {
        return "mean=" + stats.mean() + ",median=" + stats.median()
                + ",min=" + stats.min() + ",max=" + stats.max()
                + ",std=" + stats.standardDeviation();
    }

    private MissionPlan createOperationalPlan() {
        Order source = orderRepository.findAll().stream().findFirst().orElseThrow();
        Order order = orderRepository.saveAndFlush(copyOrder(source, point(200.0, -280.0)));
        Mission mission = new Mission();
        mission.setMissionCode("E2E_BENCHMARK_READ_ONLY_" + UUID.randomUUID().toString().substring(0, 8));
        mission.setStatus(MissionStatus.CREATED);
        mission.setOrder(order);
        mission = missionRepository.saveAndFlush(mission);
        return missionPlanningService.generateDirectPlan(mission.getId());
    }

    private PlanSnapshot snapshot(MissionPlan plan) {
        List<String> waypoints = plan.getWaypoints().stream().sorted(Comparator.comparingInt(PlanWaypoint::getSequence))
                .map(value -> value.getId() + "|" + value.getSequence() + "|" + value.getReason() + "|"
                        + value.getSimX() + "|" + value.getSimY() + "|" + value.getAltitudeM()).toList();
        return new PlanSnapshot(plan.getId(), plan.getPlanningAlgorithm(), plan.getPlannedDistanceM(),
                plan.getEstimatedEnergyMah(), waypoints);
    }

    private Order copyOrder(Order source, Point target) {
        Order order = new Order();
        order.setCustomer(source.getCustomer());
        order.setTitle("E2E_BENCHMARK_READ_ONLY_ORDER");
        order.setPurpose(source.getPurpose());
        order.setService(source.getService());
        order.setDescription("Temporary transactional benchmark proof record");
        order.setAddress("LOCAL_SIMULATION_METERS_GAZEBO_XY");
        order.setPoint(target);
        order.setPreferredDate(source.getPreferredDate());
        order.setPreferredTime(source.getPreferredTime());
        order.setMediaType(source.getMediaType());
        order.setDurationOfVideo(source.getDurationOfVideo());
        order.setNumberOfPhoto(source.getNumberOfPhoto());
        order.setOrderStatus(source.getOrderStatus());
        return order;
    }

    private Point point(double x, double y) {
        Point point = GEOMETRY.createPoint(new Coordinate(x, y));
        point.setSRID(4326);
        return point;
    }

    private long elapsed(long started) {
        return Math.max(0L, (System.nanoTime() - started) / 1_000_000L);
    }

    private record PlanSnapshot(String id, PlanningAlgorithm algorithm, Double distance,
            Double energy, List<String> waypoints) {
    }
}
