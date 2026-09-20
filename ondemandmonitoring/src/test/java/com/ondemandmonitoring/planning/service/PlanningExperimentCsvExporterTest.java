package com.ondemandmonitoring.planning.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.ondemandmonitoring.mission.enums.FeasibilityStatus;
import com.ondemandmonitoring.mission.enums.PlanningAlgorithm;
import com.ondemandmonitoring.planning.dto.PlanningExperimentDatasetRow;
import com.ondemandmonitoring.planning.dto.PlanningExperimentDefinition;
import com.ondemandmonitoring.planning.dto.PlanningExperimentResult;
import com.ondemandmonitoring.planning.dto.PlanningExperimentScenario;
import com.ondemandmonitoring.planning.dto.PlanningExperimentSummary;
import com.ondemandmonitoring.planning.service.impl.PlanningExperimentCsvExporterImpl;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class PlanningExperimentCsvExporterTest {

    private final PlanningExperimentCsvExporter exporter = new PlanningExperimentCsvExporterImpl();

    @Test
    void exportsDeterministicHeaderRowsEnumsNullsPrecisionAndEscapingWithoutMutation() {
        PlanningExperimentResult result = result(List.of(
                row("S001", PlanningAlgorithm.DIRECT, FeasibilityStatus.FEASIBLE,
                        123.45678901234567, 34.0, 70.5, 12.3456789012345, null, 7L, 2, null),
                row("S001", PlanningAlgorithm.ASTAR_SHORTEST, FeasibilityStatus.NO_SAFE_ROUTE,
                        null, null, null, null, null, 11L, 0, "blocked, quoted \"reason\"\nnext"),
                row("S001", PlanningAlgorithm.ASTAR_ENERGY_AWARE, FeasibilityStatus.FEASIBLE,
                        130.0, 20.0, 65.0, 10.0, 0.2, 13L, 3, null)));
        List<PlanningExperimentDatasetRow> before = List.copyOf(result.dataset());

        String csv = exporter.export(result);
        List<List<String>> records = parse(csv);

        assertThat(records.get(0)).containsExactly(
                "experiment_id", "scenario_id", "scenario_label", "home_x", "home_y",
                "target_x", "target_y", "algorithm", "feasibility_status",
                "planned_distance_m", "max_planned_altitude_m", "planned_duration_sec",
                "estimated_energy_mah", "estimated_battery_used_percent", "required_battery_percent",
                "planning_time_ms", "waypoint_count", "failure_reason");
        assertThat(records).hasSize(4);
        assertThat(records.get(1).get(7)).isEqualTo("DIRECT");
        assertThat(records.get(1).get(8)).isEqualTo("FEASIBLE");
        assertThat(records.get(1).get(9)).isEqualTo(Double.toString(123.45678901234567));
        assertThat(records.get(1).get(13)).isEmpty();
        assertThat(records.get(2).get(8)).isEqualTo("NO_SAFE_ROUTE");
        assertThat(records.get(2).get(12)).isEmpty();
        assertThat(records.get(2).get(12)).isNotEqualTo("0");
        assertThat(records.get(2).get(17)).isEqualTo("blocked, quoted \"reason\"\nnext");
        assertThat(csv).contains("\"terrain, energy \"\"tradeoff\"\"\"");
        assertThat(csv).contains("\"blocked, quoted \"\"reason\"\"\nnext\"");
        assertThat(csv.getBytes(StandardCharsets.UTF_8)).isNotEmpty();
        assertThat(result.dataset()).containsExactlyElementsOf(before);
    }

    @Test
    void preservesDatasetOrderingAndExportsOneDataRowPerDatasetRow() {
        List<PlanningExperimentDatasetRow> rows = new ArrayList<>();
        for (int i = 1; i <= 8; i++) {
            String id = "S%03d".formatted(i);
            rows.add(row(id, PlanningAlgorithm.DIRECT, FeasibilityStatus.FEASIBLE, 1.0, 1.0, 1.0, 1.0, 0.02, 1L, 2, null));
            rows.add(row(id, PlanningAlgorithm.ASTAR_SHORTEST, FeasibilityStatus.FEASIBLE, 2.0, 2.0, 2.0, 2.0, 0.04, 2L, 2, null));
            rows.add(row(id, PlanningAlgorithm.ASTAR_ENERGY_AWARE, FeasibilityStatus.FEASIBLE, 3.0, 3.0, 3.0, 3.0, 0.06, 3L, 2, null));
        }

        List<List<String>> records = parse(exporter.export(result(rows)));

        assertThat(records).hasSize(25);
        assertThat(records.subList(1, records.size())).extracting(record -> record.get(1) + "|" + record.get(7))
                .containsExactly(
                        "S001|DIRECT", "S001|ASTAR_SHORTEST", "S001|ASTAR_ENERGY_AWARE",
                        "S002|DIRECT", "S002|ASTAR_SHORTEST", "S002|ASTAR_ENERGY_AWARE",
                        "S003|DIRECT", "S003|ASTAR_SHORTEST", "S003|ASTAR_ENERGY_AWARE",
                        "S004|DIRECT", "S004|ASTAR_SHORTEST", "S004|ASTAR_ENERGY_AWARE",
                        "S005|DIRECT", "S005|ASTAR_SHORTEST", "S005|ASTAR_ENERGY_AWARE",
                        "S006|DIRECT", "S006|ASTAR_SHORTEST", "S006|ASTAR_ENERGY_AWARE",
                        "S007|DIRECT", "S007|ASTAR_SHORTEST", "S007|ASTAR_ENERGY_AWARE",
                        "S008|DIRECT", "S008|ASTAR_SHORTEST", "S008|ASTAR_ENERGY_AWARE");
    }

    static List<List<String>> parse(String csv) {
        List<List<String>> records = new ArrayList<>();
        List<String> record = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < csv.length(); i++) {
            char c = csv.charAt(i);
            if (quoted) {
                if (c == '"' && i + 1 < csv.length() && csv.charAt(i + 1) == '"') {
                    field.append('"');
                    i++;
                } else if (c == '"') quoted = false;
                else field.append(c);
            } else if (c == '"') quoted = true;
            else if (c == ',') {
                record.add(field.toString());
                field.setLength(0);
            } else if (c == '\n') {
                record.add(field.toString());
                records.add(record);
                record = new ArrayList<>();
                field.setLength(0);
            } else if (c != '\r') field.append(c);
        }
        if (!field.isEmpty() || !record.isEmpty()) {
            record.add(field.toString());
            records.add(record);
        }
        return records;
    }

    private PlanningExperimentResult result(List<PlanningExperimentDatasetRow> rows) {
        return new PlanningExperimentResult(
                new PlanningExperimentDefinition("EXP", "Research", 0.0, -280.0,
                        List.of(new PlanningExperimentScenario("S001", "terrain, energy \"tradeoff\"", 200.0, -280.0),
                                new PlanningExperimentScenario("S002", "north", 0.0, -100.0),
                                new PlanningExperimentScenario("S003", "east", 100.0, -280.0),
                                new PlanningExperimentScenario("S004", "west", -100.0, -280.0),
                                new PlanningExperimentScenario("S005", "south", 0.0, -400.0),
                                new PlanningExperimentScenario("S006", "ne", 200.0, 0.0),
                                new PlanningExperimentScenario("S007", "se", 300.0, -100.0),
                                new PlanningExperimentScenario("S008", "nw", -400.0, -100.0))),
                9.4, List.of(), rows, new PlanningExperimentSummary(List.of(), 0,
                null, null, null, null, null, null, null, 0, 0, 0, 0), 0L);
    }

    private PlanningExperimentDatasetRow row(String scenarioId, PlanningAlgorithm algorithm, FeasibilityStatus status,
            Double distance, Double z, Double duration, Double energy, Double battery,
            Long planningMs, Integer waypoints, String reason) {
        return new PlanningExperimentDatasetRow("EXP", scenarioId, 0.0, -280.0, 200.0, -280.0,
                algorithm, status, distance, z, duration, energy, battery, null, planningMs, waypoints, reason);
    }
}
