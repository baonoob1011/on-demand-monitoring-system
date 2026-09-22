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
import com.ondemandmonitoring.planning.dto.AlgorithmExperimentSummary;
import com.ondemandmonitoring.planning.dto.PlanningExperimentDatasetRow;
import com.ondemandmonitoring.planning.dto.PlanningExperimentDefinition;
import com.ondemandmonitoring.planning.dto.PlanningExperimentResult;
import com.ondemandmonitoring.planning.dto.PlanningExperimentScenario;
import com.ondemandmonitoring.planning.dto.response.EnvironmentSample;
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
class PlanningExperimentRunnerE2ETest {

    private static final double TOLERANCE = 1.0e-9;
    private static final GeometryFactory GEOMETRY = new GeometryFactory(new PrecisionModel(), 4326);

    @Autowired private PlanningExperimentRunner runner;
    @Autowired private PlanningEnvironment environment;
    @Autowired private MissionPlanningService missionPlanningService;
    @Autowired private MissionRepository missionRepository;
    @Autowired private MissionPlanRepository missionPlanRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private EntityManager entityManager;

    @Test
    void runsDeterministicRealDatasetAndDoesNotMutateOperationalPlan() {
        List<PlanningExperimentScenario> scenarios = List.of(
                scenario("S001", "terrain-energy tradeoff", 200.0, -280.0),
                scenario("S002", "airport detour", -400.0, -280.0),
                scenario("S003", "restricted target", -200.0, -280.0),
                scenario("S004", "short east route", 100.0, -280.0),
                scenario("S005", "north route", 0.0, -100.0),
                scenario("S006", "northeast route", 200.0, 0.0),
                scenario("S007", "southeast route", 300.0, -100.0),
                scenario("S008", "northwest route", -400.0, -100.0));
        sampleAndValidateTargets(scenarios);

        MissionPlan operational = createOperationalPlan();
        entityManager.flush();
        entityManager.clear();
        PlanSnapshot before = snapshot(missionPlanRepository.findById(operational.getId()).orElseThrow());

        PlanningExperimentDefinition definition = new PlanningExperimentDefinition(
                "BASELINE_REAL_V1", "Deterministic real-map baseline", 0.0, -280.0, scenarios);
        PlanningExperimentResult result = runner.run(definition);

        assertThat(result.scenarioResults()).hasSize(8);
        assertThat(result.dataset()).hasSize(24);
        assertThat(result.dataset()).extracting(PlanningExperimentDatasetRow::scenarioId)
                .containsExactly(
                        "S001", "S001", "S001", "S002", "S002", "S002",
                        "S003", "S003", "S003", "S004", "S004", "S004",
                        "S005", "S005", "S005", "S006", "S006", "S006",
                        "S007", "S007", "S007", "S008", "S008", "S008");
        Set<String> keys = new HashSet<>();
        assertThat(result.dataset()).allMatch(row ->
                keys.add(row.experimentId() + "|" + row.scenarioId() + "|" + row.algorithm()));
        verifyKnownScenarios(result);
        independentlyVerifySummary(result);

        entityManager.flush();
        entityManager.clear();
        PlanSnapshot after = snapshot(missionPlanRepository.findById(before.id()).orElseThrow());
        assertThat(after).isEqualTo(before);
        print(result, scenarios);
        System.out.printf("EXPERIMENT_E2E|READ_ONLY|plan=%s|algorithm=%s|energy=%.12f|waypoints=%d|unchanged=%s%n",
                after.id(), after.algorithm(), after.energy(), after.waypoints().size(), after.equals(before));
    }

    private void sampleAndValidateTargets(List<PlanningExperimentScenario> scenarios) {
        for (PlanningExperimentScenario scenario : scenarios) {
            EnvironmentSample sample = environment.sample(scenario.targetX(), scenario.targetY());
            assertThat(sample.insideWorldBounds()).as(scenario.scenarioId()).isTrue();
            assertThat(sample.surfaceElevationM()).as(scenario.scenarioId()).isNotNull();
            if (scenario.scenarioId().equals("S003")) assertThat(sample.restricted()).isTrue();
            else assertThat(sample.restricted()).as(scenario.scenarioId()).isFalse();
            System.out.printf("EXPERIMENT_E2E|TARGET|scenario=%s|x=%.3f|y=%.3f|inside=%s|terrain=%s|surface=%s|restricted=%s|label=%s%n",
                    scenario.scenarioId(), scenario.targetX(), scenario.targetY(), sample.insideWorldBounds(),
                    sample.terrainElevationM(), sample.surfaceElevationM(), sample.restricted(), scenario.label());
        }
    }

    private void verifyKnownScenarios(PlanningExperimentResult result) {
        List<PlanningExperimentDatasetRow> s1 = rows(result, "S001");
        PlanningExperimentDatasetRow shortest = row(s1, PlanningAlgorithm.ASTAR_SHORTEST);
        PlanningExperimentDatasetRow aware = row(s1, PlanningAlgorithm.ASTAR_ENERGY_AWARE);
        assertThat(aware.plannedDistanceM()).isGreaterThan(shortest.plannedDistanceM());
        assertThat(aware.maxPlannedAltitudeM()).isLessThan(shortest.maxPlannedAltitudeM());
        assertThat(aware.estimatedEnergyMah()).isLessThan(shortest.estimatedEnergyMah());

        List<PlanningExperimentDatasetRow> s2 = rows(result, "S002");
        assertThat(row(s2, PlanningAlgorithm.DIRECT).feasibilityStatus()).isEqualTo(FeasibilityStatus.NO_SAFE_ROUTE);
        assertThat(row(s2, PlanningAlgorithm.ASTAR_SHORTEST).feasibilityStatus()).isEqualTo(FeasibilityStatus.FEASIBLE);
        assertThat(row(s2, PlanningAlgorithm.ASTAR_ENERGY_AWARE).feasibilityStatus()).isEqualTo(FeasibilityStatus.FEASIBLE);

        assertThat(rows(result, "S003")).hasSize(3).allMatch(value ->
                value.feasibilityStatus() == FeasibilityStatus.NO_SAFE_ROUTE
                        && value.estimatedEnergyMah() == null);
    }

    private void independentlyVerifySummary(PlanningExperimentResult result) {
        for (AlgorithmExperimentSummary summary : result.summary().algorithms()) {
            List<PlanningExperimentDatasetRow> rows = result.dataset().stream()
                    .filter(value -> value.algorithm() == summary.algorithm()).toList();
            List<PlanningExperimentDatasetRow> feasible = rows.stream()
                    .filter(value -> value.feasibilityStatus() == FeasibilityStatus.FEASIBLE).toList();
            assertThat(summary.totalScenarios()).isEqualTo(rows.size());
            assertThat(summary.feasibleScenarios()).isEqualTo(feasible.size());
            assertThat(summary.feasibilityRatePercent()).isCloseTo(feasible.size() * 100.0 / rows.size(), offset());
            if (!feasible.isEmpty()) {
                assertThat(summary.meanDistanceM()).isCloseTo(
                        feasible.stream().mapToDouble(PlanningExperimentDatasetRow::plannedDistanceM).average().orElseThrow(), offset());
                assertThat(summary.meanDurationSec()).isCloseTo(
                        feasible.stream().mapToDouble(PlanningExperimentDatasetRow::plannedDurationSec).average().orElseThrow(), offset());
                assertThat(summary.meanEnergyMah()).isCloseTo(
                        feasible.stream().mapToDouble(PlanningExperimentDatasetRow::estimatedEnergyMah).average().orElseThrow(), offset());
            }
            assertThat(summary.meanPlanningTimeMs()).isCloseTo(
                    rows.stream().mapToLong(PlanningExperimentDatasetRow::planningTimeMs).average().orElseThrow(), offset());
        }
        long comparable = result.scenarioResults().stream().filter(value ->
                value.comparison().energyAwareVsShortest().energySavingPercent() != null).count();
        assertThat(result.summary().comparisonScenarioCount()).isEqualTo((int) comparable);
        double meanSavingPercent = result.scenarioResults().stream()
                .map(value -> value.comparison().energyAwareVsShortest().energySavingPercent())
                .filter(java.util.Objects::nonNull).mapToDouble(Double::doubleValue).average().orElseThrow();
        assertThat(result.summary().meanEnergySavingPercent()).isCloseTo(meanSavingPercent, offset());
    }

    private MissionPlan createOperationalPlan() {
        Order source = orderRepository.findAll().stream().findFirst().orElseThrow();
        Order order = orderRepository.saveAndFlush(copyOrder(source, point(200.0, -280.0)));
        Mission mission = new Mission();
        mission.setMissionCode("E2E_EXPERIMENT_READ_ONLY_" + UUID.randomUUID().toString().substring(0, 8));
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

    private void print(PlanningExperimentResult result, List<PlanningExperimentScenario> scenarios) {
        for (PlanningExperimentDatasetRow row : result.dataset()) {
            System.out.printf("EXPERIMENT_E2E|ROW|scenario=%s|algorithm=%s|status=%s|distance=%s|worldZ=%s|duration=%s|energy=%s|battery=%s|required=%s|planningMs=%d|waypoints=%d%n",
                    row.scenarioId(), row.algorithm(), row.feasibilityStatus(), row.plannedDistanceM(),
                    row.maxPlannedAltitudeM(), row.plannedDurationSec(), row.estimatedEnergyMah(),
                    row.estimatedBatteryUsedPercent(), row.requiredBatteryPercent(), row.planningTimeMs(), row.waypointCount());
        }
        for (AlgorithmExperimentSummary summary : result.summary().algorithms()) {
            System.out.printf("EXPERIMENT_E2E|SUMMARY|algorithm=%s|total=%d|feasible=%d|infeasible=%d|rate=%.6f|meanDistance=%s|meanDuration=%s|meanEnergy=%s|meanBattery=%s|meanPlanning=%s|minPlanning=%s|maxPlanning=%s%n",
                    summary.algorithm(), summary.totalScenarios(), summary.feasibleScenarios(), summary.infeasibleScenarios(),
                    summary.feasibilityRatePercent(), summary.meanDistanceM(), summary.meanDurationSec(),
                    summary.meanEnergyMah(), summary.meanBatteryUsedPercent(), summary.meanPlanningTimeMs(),
                    summary.minPlanningTimeMs(), summary.maxPlanningTimeMs());
        }
        System.out.printf("EXPERIMENT_E2E|TRADEOFF_SUMMARY|comparisonCount=%d|meanEnergySaving=%s|meanEnergyPercent=%s|meanDistanceDifference=%s|meanDistancePercent=%s|meanDurationDifference=%s|meanAltitudeDifference=%s|meanPlanningDifference=%s|savingCount=%d|equalCount=%d|longerLowerCount=%d|lowerAltitudeLowerCount=%d|scenarios=%d|rows=%d|elapsedMs=%d%n",
                result.summary().comparisonScenarioCount(), result.summary().meanEnergySavingMah(),
                result.summary().meanEnergySavingPercent(), result.summary().meanDistanceDifferenceM(),
                result.summary().meanDistanceDifferencePercent(), result.summary().meanDurationDifferenceSec(),
                result.summary().meanAltitudeDifferenceM(), result.summary().meanPlanningTimeDifferenceMs(),
                result.summary().energySavingScenarioCount(), result.summary().equalEnergyScenarioCount(),
                result.summary().longerButLowerEnergyCount(), result.summary().lowerAltitudeAndLowerEnergyCount(),
                scenarios.size(), result.dataset().size(), result.totalExperimentElapsedMs());
    }

    private List<PlanningExperimentDatasetRow> rows(PlanningExperimentResult result, String scenarioId) {
        return result.dataset().stream().filter(value -> value.scenarioId().equals(scenarioId)).toList();
    }

    private PlanningExperimentDatasetRow row(List<PlanningExperimentDatasetRow> rows, PlanningAlgorithm algorithm) {
        return rows.stream().filter(value -> value.algorithm() == algorithm).findFirst().orElseThrow();
    }

    private PlanningExperimentScenario scenario(String id, String label, double x, double y) {
        return new PlanningExperimentScenario(id, label, x, y);
    }

    private Order copyOrder(Order source, Point target) {
        Order order = new Order();
        order.setCustomer(source.getCustomer());
        order.setTitle("E2E_EXPERIMENT_READ_ONLY_ORDER");
        order.setService(source.getService());
        order.setDescription("Temporary transactional experiment proof record");
        order.setAddress("LOCAL_SIMULATION_METERS_GAZEBO_XY");
        order.setPoint(target);
        order.setPreferredDateFrom(source.getPreferredDateFrom());
        order.setPreferredDateTo(source.getPreferredDateTo());
        order.setPreferredTime(source.getPreferredTime());
        order.setOrderStatus(source.getOrderStatus());
        return order;
    }

    private Point point(double x, double y) {
        Point point = GEOMETRY.createPoint(new Coordinate(x, y));
        point.setSRID(4326);
        return point;
    }

    private org.assertj.core.data.Offset<Double> offset() {
        return org.assertj.core.data.Offset.offset(TOLERANCE);
    }

    private record PlanSnapshot(String id, PlanningAlgorithm algorithm, Double distance,
            Double energy, List<String> waypoints) {
    }
}
