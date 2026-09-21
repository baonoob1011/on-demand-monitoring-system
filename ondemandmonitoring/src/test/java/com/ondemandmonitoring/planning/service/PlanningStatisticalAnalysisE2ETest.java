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
import com.ondemandmonitoring.planning.dto.MetricStatisticalAnalysis;
import com.ondemandmonitoring.planning.dto.PairedAlgorithmAnalysis;
import com.ondemandmonitoring.planning.dto.PlanningBenchmarkDefinition;
import com.ondemandmonitoring.planning.dto.PlanningBenchmarkGenerationResult;
import com.ondemandmonitoring.planning.dto.PlanningExperimentDefinition;
import com.ondemandmonitoring.planning.dto.PlanningExperimentResult;
import com.ondemandmonitoring.planning.dto.PlanningResearchAnalysis;
import com.ondemandmonitoring.planning.dto.PlanningStatisticalAnalysis;
import jakarta.persistence.EntityManager;
import java.util.Comparator;
import java.util.List;
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
class PlanningStatisticalAnalysisE2ETest {

    private static final GeometryFactory GEOMETRY = new GeometryFactory(new PrecisionModel(), 4326);

    @Autowired private PlanningBenchmarkScenarioGenerator generator;
    @Autowired private PlanningExperimentRunner runner;
    @Autowired private PlanningResearchAnalysisService researchAnalysisService;
    @Autowired private PlanningStatisticalAnalysisService statisticalAnalysisService;
    @Autowired private PlanningExperimentCsvExporter csvExporter;
    @Autowired private MissionPlanningService missionPlanningService;
    @Autowired private MissionRepository missionRepository;
    @Autowired private MissionPlanRepository missionPlanRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private EntityManager entityManager;

    @Test
    void runsFormalStatisticsOnRealBenchmarkWithoutMutation() {
        MissionPlan operational = createOperationalPlan();
        long missionsBefore = missionRepository.count();
        long plansBefore = missionPlanRepository.count();
        entityManager.flush();
        entityManager.clear();
        PlanSnapshot before = snapshot(missionPlanRepository.findById(operational.getId()).orElseThrow());

        long benchmarkStart = System.nanoTime();
        PlanningBenchmarkGenerationResult benchmark = generator.generate(benchmarkDefinition());
        PlanningExperimentResult experiment = runner.run(new PlanningExperimentDefinition(
                benchmark.definition().benchmarkId(),
                "Deterministic generated planning benchmark",
                benchmark.definition().homeX(),
                benchmark.definition().homeY(),
                benchmark.scenarios()));
        long benchmarkMs = elapsed(benchmarkStart);
        PlanningResearchAnalysis research = researchAnalysisService.analyze(experiment);
        String csv = csvExporter.export(experiment);
        long statsStart = System.nanoTime();
        PlanningStatisticalAnalysis stats = statisticalAnalysisService.analyze(experiment, research);
        long statsMs = elapsed(statsStart);
        PlanningStatisticalAnalysis repeated = statisticalAnalysisService.analyze(experiment, research);

        assertThat(repeated.primaryEnergyAnalysis()).isEqualTo(stats.primaryEnergyAnalysis());
        assertThat(repeated.secondaryMetricAnalyses()).isEqualTo(stats.secondaryMetricAnalyses());
        assertThat(repeated.energySavingPercentMeanConfidenceInterval())
                .isEqualTo(stats.energySavingPercentMeanConfidenceInterval());
        assertThat(experiment.dataset()).hasSize(225);
        assertThat(PlanningExperimentCsvExporterTest.parse(csv)).hasSize(226);
        assertThat(stats.pairedScenarioCount()).isEqualTo(75);
        assertThat(stats.primaryEnergyAnalysis().distribution().n()).isEqualTo(75);
        assertThat(stats.primaryEnergyAnalysis().hypothesisTest().zeroDifferenceCount()).isEqualTo(21);
        assertThat(stats.primaryEnergyAnalysis().hypothesisTest().alternative()).isEqualTo("two-sided");
        assertThat(stats.secondaryMetricAnalyses()).hasSize(4).allMatch(metric -> metric.holmAdjustedPValue() != null);

        entityManager.flush();
        entityManager.clear();
        assertThat(snapshot(missionPlanRepository.findById(before.id()).orElseThrow())).isEqualTo(before);
        assertThat(missionRepository.count()).isEqualTo(missionsBefore);
        assertThat(missionPlanRepository.count()).isEqualTo(plansBefore);
        print(research.shortestVsEnergyAware(), stats, benchmarkMs, statsMs);
    }

    private PlanningBenchmarkDefinition benchmarkDefinition() {
        return new PlanningBenchmarkDefinition("BENCHMARK_REAL_V1", 0.0, -280.0, 50.0, 75, 50.0, 5, 5);
    }

    private void print(PairedAlgorithmAnalysis paired, PlanningStatisticalAnalysis stats,
            long benchmarkMs, long statsMs) {
        MetricStatisticalAnalysis energy = stats.primaryEnergyAnalysis();
        System.out.printf("STAT_E2E|ENERGY|n=%d|zero=%d|mean=%s|median=%s|std=%s|q1=%s|q3=%s|iqr=%s|min=%s|max=%s|skew=%s|outliers=%d|normality=%s|normalityStatistic=%s|normalityP=%s|test=%s|statistic=%s|effectiveN=%d|df=%s|p=%s|reject=%s|effect=%s|effectValue=%s|ciLower=%s|ciUpper=%s|percentCiLower=%s|percentCiUpper=%s|savingCount=%d|equalCount=%d|higherCount=%d|benchmarkMs=%d|statsMs=%d%n",
                energy.distribution().n(), energy.hypothesisTest().zeroDifferenceCount(),
                energy.distribution().mean(), energy.distribution().median(),
                energy.distribution().sampleStandardDeviation(), energy.distribution().q1(),
                energy.distribution().q3(), energy.distribution().iqr(), energy.distribution().min(),
                energy.distribution().max(), energy.distribution().skewness(),
                energy.distribution().outliers().outlierCount(), energy.normality().method(),
                energy.normality().statistic(), energy.normality().pValue(),
                energy.hypothesisTest().testName(), energy.hypothesisTest().statistic(),
                energy.hypothesisTest().effectiveN(), energy.hypothesisTest().degreesOfFreedom(),
                energy.hypothesisTest().pValue(), energy.hypothesisTest().rejectNull(),
                energy.effectSize().type(), energy.effectSize().value(),
                energy.meanConfidenceInterval().lower(), energy.meanConfidenceInterval().upper(),
                stats.energySavingPercentMeanConfidenceInterval().lower(),
                stats.energySavingPercentMeanConfidenceInterval().upper(),
                paired.energySavingScenarioCount(), paired.equalEnergyScenarioCount(),
                paired.higherEnergyScenarioCount(), benchmarkMs, statsMs);
        for (MetricStatisticalAnalysis metric : stats.secondaryMetricAnalyses()) {
            System.out.printf("STAT_E2E|SECONDARY|metric=%s|mean=%s|median=%s|std=%s|q1=%s|q3=%s|iqr=%s|min=%s|max=%s|skew=%s|outliers=%d|normalityP=%s|test=%s|statistic=%s|effectiveN=%d|p=%s|holm=%s|reject=%s|effect=%s|effectValue=%s|ciLower=%s|ciUpper=%s%n",
                    metric.metricKey(), metric.distribution().mean(), metric.distribution().median(),
                    metric.distribution().sampleStandardDeviation(), metric.distribution().q1(),
                    metric.distribution().q3(), metric.distribution().iqr(), metric.distribution().min(),
                    metric.distribution().max(), metric.distribution().skewness(),
                    metric.distribution().outliers().outlierCount(), metric.normality().pValue(),
                    metric.hypothesisTest().testName(), metric.hypothesisTest().statistic(),
                    metric.hypothesisTest().effectiveN(), metric.rawPValue(), metric.holmAdjustedPValue(),
                    metric.holmRejected(), metric.effectSize().type(), metric.effectSize().value(),
                    metric.meanConfidenceInterval().lower(), metric.meanConfidenceInterval().upper());
        }
        System.out.printf("STAT_E2E|TIMING|shortestMean=%s|shortestMedian=%s|shortestQ1=%s|shortestQ3=%s|shortestMax=%s|awareMean=%s|awareMedian=%s|awareQ1=%s|awareQ3=%s|awareMax=%s|diffMean=%s|diffMedian=%s|diffIqr=%s|diffMax=%s|diffOutliers=%d%n",
                stats.planningTimeDistributions().shortestPlanningTimeMs().mean(),
                stats.planningTimeDistributions().shortestPlanningTimeMs().median(),
                stats.planningTimeDistributions().shortestPlanningTimeMs().q1(),
                stats.planningTimeDistributions().shortestPlanningTimeMs().q3(),
                stats.planningTimeDistributions().shortestPlanningTimeMs().max(),
                stats.planningTimeDistributions().energyAwarePlanningTimeMs().mean(),
                stats.planningTimeDistributions().energyAwarePlanningTimeMs().median(),
                stats.planningTimeDistributions().energyAwarePlanningTimeMs().q1(),
                stats.planningTimeDistributions().energyAwarePlanningTimeMs().q3(),
                stats.planningTimeDistributions().energyAwarePlanningTimeMs().max(),
                stats.planningTimeDistributions().pairedDifferenceMs().mean(),
                stats.planningTimeDistributions().pairedDifferenceMs().median(),
                stats.planningTimeDistributions().pairedDifferenceMs().iqr(),
                stats.planningTimeDistributions().pairedDifferenceMs().max(),
                stats.planningTimeDistributions().pairedDifferenceMs().outliers().outlierCount());
    }

    private MissionPlan createOperationalPlan() {
        Order source = orderRepository.findAll().stream().findFirst().orElseThrow();
        Order order = orderRepository.saveAndFlush(copyOrder(source, point(200.0, -280.0)));
        Mission mission = new Mission();
        mission.setMissionCode("E2E_STAT_READ_ONLY_" + UUID.randomUUID().toString().substring(0, 8));
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
        order.setTitle("E2E_STAT_READ_ONLY_ORDER");
        order.setPurpose(source.getPurpose());
        order.setService(source.getService());
        order.setDescription("Temporary transactional statistical proof record");
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
