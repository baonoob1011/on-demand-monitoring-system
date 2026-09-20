package com.ondemandmonitoring.planning.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.ondemandmonitoring.mission.domain.Mission;
import com.ondemandmonitoring.mission.domain.MissionPlan;
import com.ondemandmonitoring.mission.domain.PlanWaypoint;
import com.ondemandmonitoring.mission.enums.FeasibilityStatus;
import com.ondemandmonitoring.mission.enums.MissionStatus;
import com.ondemandmonitoring.mission.enums.PlanningAlgorithm;
import com.ondemandmonitoring.mission.repository.MissionPlanRepository;
import com.ondemandmonitoring.mission.repository.MissionRepository;
import com.ondemandmonitoring.order.domain.Order;
import com.ondemandmonitoring.order.repository.OrderRepository;
import com.ondemandmonitoring.planning.dto.AlgorithmDescriptiveStatistics;
import com.ondemandmonitoring.planning.dto.PairedAlgorithmAnalysis;
import com.ondemandmonitoring.planning.dto.PairedScenarioObservation;
import com.ondemandmonitoring.planning.dto.PlanningExperimentDatasetRow;
import com.ondemandmonitoring.planning.dto.PlanningExperimentDefinition;
import com.ondemandmonitoring.planning.dto.PlanningExperimentResult;
import com.ondemandmonitoring.planning.dto.PlanningExperimentScenario;
import com.ondemandmonitoring.planning.dto.PlanningResearchAnalysis;
import jakarta.persistence.EntityManager;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.assertj.core.data.Offset;
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
class PlanningResearchAnalysisE2ETest {

    private static final Offset<Double> TOL = Offset.offset(1.0e-9);
    private static final GeometryFactory GEOMETRY = new GeometryFactory(new PrecisionModel(), 4326);

    @Autowired private PlanningExperimentRunner runner;
    @Autowired private PlanningExperimentCsvExporter csvExporter;
    @Autowired private PlanningResearchAnalysisService analysisService;
    @Autowired private MissionPlanningService missionPlanningService;
    @Autowired private MissionRepository missionRepository;
    @Autowired private MissionPlanRepository missionPlanRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private EntityManager entityManager;

    @Test
    void exportsCsvAndAnalyzesRealResearchDatasetWithoutMutatingOperationalPlan() {
        MissionPlan operational = createOperationalPlan();
        entityManager.flush();
        entityManager.clear();
        PlanSnapshot before = snapshot(missionPlanRepository.findById(operational.getId()).orElseThrow());

        long experimentStart = System.nanoTime();
        PlanningExperimentResult result = runner.run(definition());
        long experimentMs = elapsed(experimentStart);
        long csvStart = System.nanoTime();
        String csv = csvExporter.export(result);
        long csvMs = elapsed(csvStart);
        long analysisStart = System.nanoTime();
        PlanningResearchAnalysis analysis = analysisService.analyze(result);
        long analysisMs = elapsed(analysisStart);

        assertThat(result.dataset()).hasSize(24);
        List<List<String>> records = PlanningExperimentCsvExporterTest.parse(csv);
        assertThat(records).hasSize(25);
        Set<String> keys = new HashSet<>();
        records.subList(1, records.size()).forEach(record -> assertThat(keys.add(record.get(1) + "|" + record.get(7))).isTrue());
        for (PlanningExperimentScenario scenario : definition().scenarios()) {
            for (PlanningAlgorithm algorithm : PlanningAlgorithm.values()) {
                assertThat(keys).contains(scenario.scenarioId() + "|" + algorithm.name());
            }
        }

        assertS001RoundTrip(records);
        independentlyVerifyPairedAnalysis(result, analysis);
        assertThat(analysis.shortestVsEnergyAware().comparableScenarioCount()).isEqualTo(7);
        assertConsistentWithExperimentSummary(result, analysis);

        entityManager.flush();
        entityManager.clear();
        PlanSnapshot after = snapshot(missionPlanRepository.findById(before.id()).orElseThrow());
        assertThat(after).isEqualTo(before);
        print(result, analysis, experimentMs, csvMs, analysisMs, after.equals(before));
    }

    private void independentlyVerifyPairedAnalysis(PlanningExperimentResult result, PlanningResearchAnalysis analysis) {
        Map<String, PlanningExperimentDatasetRow> shortest = rowsByScenario(result, PlanningAlgorithm.ASTAR_SHORTEST);
        Map<String, PlanningExperimentDatasetRow> aware = rowsByScenario(result, PlanningAlgorithm.ASTAR_ENERGY_AWARE);
        int count = 0;
        int longerLower = 0;
        double savingMah = 0.0;
        double savingPercent = 0.0;
        for (String scenarioId : shortest.keySet()) {
            PlanningExperimentDatasetRow s = shortest.get(scenarioId);
            PlanningExperimentDatasetRow a = aware.get(scenarioId);
            if (s.feasibilityStatus() != FeasibilityStatus.FEASIBLE
                    || a.feasibilityStatus() != FeasibilityStatus.FEASIBLE
                    || s.estimatedEnergyMah() == null || a.estimatedEnergyMah() == null) {
                continue;
            }
            double saving = s.estimatedEnergyMah() - a.estimatedEnergyMah();
            count++;
            savingMah += saving;
            savingPercent += saving / s.estimatedEnergyMah() * 100.0;
            if (a.plannedDistanceM() > s.plannedDistanceM() + 1.0e-9 && saving > 1.0e-9) longerLower++;
        }
        PairedAlgorithmAnalysis paired = analysis.shortestVsEnergyAware();
        assertThat(paired.comparableScenarioCount()).isEqualTo(count);
        assertThat(paired.energySavingMah().mean()).isCloseTo(savingMah / count, TOL);
        assertThat(paired.energySavingPercent().mean()).isCloseTo(savingPercent / count, TOL);
        assertThat(paired.longerButLowerEnergyCount()).isEqualTo(longerLower);
    }

    private void assertConsistentWithExperimentSummary(PlanningExperimentResult result, PlanningResearchAnalysis analysis) {
        PairedAlgorithmAnalysis paired = analysis.shortestVsEnergyAware();
        assertThat(paired.comparableScenarioCount()).isEqualTo(result.summary().comparisonScenarioCount());
        assertThat(paired.energySavingMah().mean()).isCloseTo(result.summary().meanEnergySavingMah(), TOL);
        assertThat(paired.energySavingPercent().mean()).isCloseTo(result.summary().meanEnergySavingPercent(), TOL);
        assertThat(paired.energySavingScenarioCount()).isEqualTo(result.summary().energySavingScenarioCount());
        assertThat(paired.equalEnergyScenarioCount()).isEqualTo(result.summary().equalEnergyScenarioCount());
        assertThat(paired.longerButLowerEnergyCount()).isEqualTo(result.summary().longerButLowerEnergyCount());
        assertThat(paired.lowerAltitudeAndLowerEnergyCount()).isEqualTo(result.summary().lowerAltitudeAndLowerEnergyCount());
    }

    private void assertS001RoundTrip(List<List<String>> records) {
        List<String> shortest = csvRow(records, "S001", PlanningAlgorithm.ASTAR_SHORTEST);
        List<String> aware = csvRow(records, "S001", PlanningAlgorithm.ASTAR_ENERGY_AWARE);
        assertThat(shortest.get(7)).isEqualTo("ASTAR_SHORTEST");
        assertThat(Double.parseDouble(shortest.get(9))).isCloseTo(200.0, TOL);
        assertThat(Double.parseDouble(shortest.get(10))).isCloseTo(34.0, TOL);
        assertThat(Double.parseDouble(shortest.get(12))).isCloseTo(204.027777777778, TOL);
        assertThat(aware.get(7)).isEqualTo("ASTAR_ENERGY_AWARE");
        assertThat(Double.parseDouble(aware.get(9))).isCloseTo(218.5269119345814, TOL);
        assertThat(Double.parseDouble(aware.get(10))).isCloseTo(19.5, TOL);
        assertThat(Double.parseDouble(aware.get(12))).isCloseTo(187.971946616694, TOL);
    }

    private Map<String, PlanningExperimentDatasetRow> rowsByScenario(
            PlanningExperimentResult result, PlanningAlgorithm algorithm) {
        Map<String, PlanningExperimentDatasetRow> rows = new HashMap<>();
        result.dataset().stream().filter(value -> value.algorithm() == algorithm)
                .forEach(value -> rows.put(value.scenarioId(), value));
        return rows;
    }

    private List<String> csvRow(List<List<String>> records, String scenarioId, PlanningAlgorithm algorithm) {
        return records.stream()
                .filter(record -> record.size() > 7 && record.get(1).equals(scenarioId)
                        && record.get(7).equals(algorithm.name()))
                .findFirst().orElseThrow();
    }

    private PlanningExperimentDefinition definition() {
        return new PlanningExperimentDefinition("BASELINE_REAL_V1", "Deterministic real-map baseline", 0.0, -280.0,
                List.of(
                        new PlanningExperimentScenario("S001", "terrain-energy tradeoff", 200.0, -280.0),
                        new PlanningExperimentScenario("S002", "airport detour", -400.0, -280.0),
                        new PlanningExperimentScenario("S003", "restricted target", -200.0, -280.0),
                        new PlanningExperimentScenario("S004", "short east route", 100.0, -280.0),
                        new PlanningExperimentScenario("S005", "north route", 0.0, -100.0),
                        new PlanningExperimentScenario("S006", "northeast route", 200.0, 0.0),
                        new PlanningExperimentScenario("S007", "southeast route", 300.0, -100.0),
                        new PlanningExperimentScenario("S008", "northwest route", -400.0, -100.0)));
    }

    private MissionPlan createOperationalPlan() {
        Order source = orderRepository.findAll().stream().findFirst().orElseThrow();
        Order order = orderRepository.saveAndFlush(copyOrder(source, point(200.0, -280.0)));
        Mission mission = new Mission();
        mission.setMissionCode("E2E_RESEARCH_READ_ONLY_" + UUID.randomUUID().toString().substring(0, 8));
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
        order.setTitle("E2E_RESEARCH_READ_ONLY_ORDER");
        order.setPurpose(source.getPurpose());
        order.setService(source.getService());
        order.setDescription("Temporary transactional research proof record");
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

    private void print(PlanningExperimentResult result, PlanningResearchAnalysis analysis,
            long experimentMs, long csvMs, long analysisMs, boolean unchanged) {
        for (AlgorithmDescriptiveStatistics stats : analysis.algorithms()) {
            System.out.printf("RESEARCH_E2E|ALGORITHM|algorithm=%s|total=%d|feasible=%d|infeasible=%d|rate=%s|distanceMean=%s|distanceMedian=%s|distanceStd=%s|durationMean=%s|durationMedian=%s|durationStd=%s|energyMean=%s|energyMedian=%s|energyStd=%s|batteryMean=%s|batteryMedian=%s|batteryStd=%s|planningMean=%s|planningMedian=%s|planningStd=%s%n",
                    stats.algorithm(), stats.totalScenarioCount(), stats.feasibleScenarioCount(), stats.infeasibleScenarioCount(),
                    stats.feasibilityRatePercent(), stats.distanceM().mean(), stats.distanceM().median(),
                    stats.distanceM().standardDeviation(), stats.durationSec().mean(), stats.durationSec().median(),
                    stats.durationSec().standardDeviation(), stats.energyMah().mean(), stats.energyMah().median(),
                    stats.energyMah().standardDeviation(), stats.batteryUsedPercent().mean(),
                    stats.batteryUsedPercent().median(), stats.batteryUsedPercent().standardDeviation(),
                    stats.planningTimeMs().mean(), stats.planningTimeMs().median(), stats.planningTimeMs().standardDeviation());
        }
        PairedAlgorithmAnalysis paired = analysis.shortestVsEnergyAware();
        System.out.printf("RESEARCH_E2E|PAIRED|count=%d|saving=%d|equal=%d|higher=%d|longerLower=%d|lowerAltitudeLower=%d|energyMean=%s|energyMedian=%s|energyMin=%s|energyMax=%s|energyStd=%s|energyPercentMean=%s|energyPercentMedian=%s|energyPercentMin=%s|energyPercentMax=%s|energyPercentStd=%s|distanceMean=%s|distancePercentMean=%s|durationMean=%s|durationPercentMean=%s|altitudeMean=%s|planningMean=%s|experimentMs=%d|csvMs=%d|analysisMs=%d|summaryCount=%d|rows=%d|readOnly=%s%n",
                paired.comparableScenarioCount(), paired.energySavingScenarioCount(), paired.equalEnergyScenarioCount(),
                paired.higherEnergyScenarioCount(), paired.longerButLowerEnergyCount(),
                paired.lowerAltitudeAndLowerEnergyCount(), paired.energySavingMah().mean(),
                paired.energySavingMah().median(), paired.energySavingMah().min(), paired.energySavingMah().max(),
                paired.energySavingMah().standardDeviation(), paired.energySavingPercent().mean(),
                paired.energySavingPercent().median(), paired.energySavingPercent().min(), paired.energySavingPercent().max(),
                paired.energySavingPercent().standardDeviation(), paired.distanceDifferenceM().mean(),
                paired.distanceDifferencePercent().mean(), paired.durationDifferenceSec().mean(),
                paired.durationDifferencePercent().mean(), paired.altitudeDifferenceM().mean(),
                paired.planningTimeDifferenceMs().mean(), experimentMs, csvMs, analysisMs,
                result.summary().comparisonScenarioCount(), result.dataset().size(), unchanged);
        for (PairedScenarioObservation observation : paired.observations()) {
            System.out.printf("RESEARCH_E2E|OBS|scenario=%s|shortestDistance=%s|awareDistance=%s|distancePercent=%s|shortestZ=%s|awareZ=%s|shortestEnergy=%s|awareEnergy=%s|savingMah=%s|savingPercent=%s%n",
                    observation.scenarioId(), observation.shortestDistanceM(), observation.energyAwareDistanceM(),
                    observation.distanceDifferencePercent(), observation.shortestWorldZM(), observation.energyAwareWorldZM(),
                    observation.shortestEnergyMah(), observation.energyAwareEnergyMah(),
                    observation.energySavingMah(), observation.energySavingPercent());
        }
    }

    private record PlanSnapshot(String id, PlanningAlgorithm algorithm, Double distance,
            Double energy, List<String> waypoints) {
    }
}
