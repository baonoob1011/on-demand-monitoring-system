package com.ondemandmonitoring.planning.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ondemandmonitoring.planning.dto.PlanningBenchmarkDefinition;
import com.ondemandmonitoring.planning.dto.PlanningBenchmarkGenerationResult;
import com.ondemandmonitoring.planning.dto.PlanningExperimentDefinition;
import com.ondemandmonitoring.planning.dto.PlanningExperimentResult;
import com.ondemandmonitoring.planning.dto.PlanningResearchAnalysis;
import com.ondemandmonitoring.planning.dto.PlanningResearchArtifactBundle;
import com.ondemandmonitoring.planning.dto.PlanningResearchArtifactWriteResult;
import com.ondemandmonitoring.planning.dto.PlanningStatisticalAnalysis;
import com.ondemandmonitoring.mission.repository.MissionPlanRepository;
import com.ondemandmonitoring.mission.repository.MissionRepository;
import com.ondemandmonitoring.order.repository.OrderRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
@ActiveProfiles("e2e")
class PlanningResearchArtifactE2ETest {

    @Autowired private PlanningBenchmarkScenarioGenerator generator;
    @Autowired private PlanningExperimentRunner runner;
    @Autowired private PlanningResearchAnalysisService researchAnalysisService;
    @Autowired private PlanningStatisticalAnalysisService statisticalAnalysisService;
    @Autowired private PlanningResearchArtifactService artifactService;
    @Autowired private PlanningResearchArtifactWriter artifactWriter;
    @Autowired private MissionRepository missionRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private MissionPlanRepository missionPlanRepository;

    @Test
    void writesThesisArtifactsForRealBenchmarkAndVerifiesConsistency() throws Exception {
        long missionsBefore = missionRepository.count();
        long ordersBefore = orderRepository.count();
        long plansBefore = missionPlanRepository.count();

        long benchmarkStart = System.nanoTime();
        PlanningBenchmarkGenerationResult benchmark = generator.generate(
                new PlanningBenchmarkDefinition("BENCHMARK_REAL_V1", 0.0, -280.0, 50.0, 75, 50.0, 5, 5));
        PlanningExperimentResult experiment = runner.run(new PlanningExperimentDefinition(
                benchmark.definition().benchmarkId(), "Deterministic generated planning benchmark",
                benchmark.definition().homeX(), benchmark.definition().homeY(), benchmark.scenarios()));
        long benchmarkMs = elapsed(benchmarkStart);
        long researchStart = System.nanoTime();
        PlanningResearchAnalysis research = researchAnalysisService.analyze(experiment);
        long researchMs = elapsed(researchStart);
        long statsStart = System.nanoTime();
        PlanningStatisticalAnalysis statistics = statisticalAnalysisService.analyze(experiment, research);
        long statsMs = elapsed(statsStart);
        long buildStart = System.nanoTime();
        PlanningResearchArtifactBundle bundle = artifactService.build(experiment, research, statistics);
        PlanningResearchArtifactBundle repeated = artifactService.build(experiment, research, statistics);
        long buildMs = elapsed(buildStart);
        assertThat(repeated).isEqualTo(bundle);

        Path output = Path.of("target", "research-artifacts");
        long writeStart = System.nanoTime();
        PlanningResearchArtifactWriteResult write = artifactWriter.write(bundle, output);
        long writeMs = elapsed(writeStart);

        assertThat(write.writtenFiles()).hasSize(11).allMatch(Files::exists);
        assertRows(output.resolve("algorithm-summary.csv"), 3);
        assertRows(output.resolve("paired-scenarios.csv"), 75);
        assertRows(output.resolve("statistical-summary.csv"), 5);
        assertRows(output.resolve("figure-energy-saving.csv"), 75);
        assertRows(output.resolve("figure-distance-energy-tradeoff.csv"), 75);
        assertRows(output.resolve("figure-world-z-difference.csv"), 75);
        assertRows(output.resolve("figure-planning-time.csv"), 75);
        assertRows(output.resolve("figure-energy-distribution.csv"), 150);
        assertRows(output.resolve("figure-planning-time-distribution.csv"), 150);

        ObjectMapper mapper = new ObjectMapper();
        JsonNode manifest = mapper.readTree(Files.readString(output.resolve("research-artifact-manifest.json")));
        assertThat(manifest.get("scenarioCount").asInt()).isEqualTo(75);
        assertThat(manifest.get("datasetRowCount").asInt()).isEqualTo(225);
        assertThat(manifest.get("pairedScenarioCount").asInt()).isEqualTo(75);
        assertThat(manifest.get("coordinateSystem").asText()).isEqualTo("LOCAL_SIMULATION_METERS_GAZEBO_XY");
        assertThat(manifest.get("altitudeSemantics").asText()).isEqualTo("GAZEBO_WORLD_Z_METERS");
        assertThat(manifest.get("energySemantics").asText()).isEqualTo("PREDICTED_MISSION_ENERGY_MAH");
        assertThat(manifest.get("timingSemantics").asText()).isEqualTo("WALL_CLOCK_PLANNING_TIME_MS");
        assertThat(manifest.get("algorithms"))
                .extracting(JsonNode::asText)
                .containsExactly("DIRECT", "ASTAR_SHORTEST", "ASTAR_ENERGY_AWARE");

        int mismatches = consistencyMismatches(Files.readString(output.resolve("paired-scenarios.csv")));
        assertThat(mismatches).isZero();
        assertThat(Files.readString(output.resolve("paired-scenarios.csv")))
                .contains("145.10416666666663");
        assertThat(missionRepository.count()).isEqualTo(missionsBefore);
        assertThat(orderRepository.count()).isEqualTo(ordersBefore);
        assertThat(missionPlanRepository.count()).isEqualTo(plansBefore);
        System.out.printf("ARTIFACT_E2E|files=%d|algorithmRows=3|pairedRows=75|statRows=5|energyDistributionRows=150|planningTimeDistributionRows=150|benchmarkMs=%d|researchMs=%d|statsMs=%d|buildMs=%d|writeMs=%d|zeroEnergyRows=%d|mismatches=%d|missionsBefore=%d|missionsAfter=%d|ordersBefore=%d|ordersAfter=%d|plansBefore=%d|plansAfter=%d|output=%s%n",
                write.writtenFiles().size(), benchmarkMs, researchMs, statsMs, buildMs, writeMs,
                research.shortestVsEnergyAware().equalEnergyScenarioCount(), mismatches,
                missionsBefore, missionRepository.count(), ordersBefore, orderRepository.count(),
                plansBefore, missionPlanRepository.count(),
                write.outputDirectory());
    }

    private void assertRows(Path path, int expectedDataRows) throws Exception {
        assertThat(PlanningExperimentCsvExporterTest.parse(Files.readString(path))).hasSize(expectedDataRows + 1);
    }

    private int consistencyMismatches(String csv) {
        List<List<String>> rows = PlanningExperimentCsvExporterTest.parse(csv);
        int mismatches = 0;
        for (int i = 1; i < rows.size(); i++) {
            List<String> r = rows.get(i);
            if (!close(d(r.get(4)), d(r.get(3)) - d(r.get(2)))) mismatches++;
            if (!close(d(r.get(8)), d(r.get(7)) - d(r.get(6)))) mismatches++;
            if (!close(d(r.get(12)), d(r.get(11)) - d(r.get(10)))) mismatches++;
            if (!close(d(r.get(15)), d(r.get(13)) - d(r.get(14)))) mismatches++;
            if (Long.parseLong(r.get(19)) != Long.parseLong(r.get(18)) - Long.parseLong(r.get(17))) mismatches++;
        }
        return mismatches;
    }

    private double d(String value) {
        return Double.parseDouble(value);
    }

    private boolean close(double a, double b) {
        return Math.abs(a - b) <= 1.0e-6;
    }

    private long elapsed(long started) {
        return Math.max(0L, (System.nanoTime() - started) / 1_000_000L);
    }
}
