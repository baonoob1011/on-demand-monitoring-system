package com.ondemandmonitoring.planning.service.impl;

import com.ondemandmonitoring.planning.dto.PlanningExperimentDatasetRow;
import com.ondemandmonitoring.planning.dto.PlanningExperimentResult;
import com.ondemandmonitoring.planning.service.PlanningExperimentCsvExporter;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class PlanningExperimentCsvExporterImpl implements PlanningExperimentCsvExporter {

    public static final List<String> HEADER = List.of(
            "experiment_id",
            "scenario_id",
            "scenario_label",
            "home_x",
            "home_y",
            "target_x",
            "target_y",
            "algorithm",
            "feasibility_status",
            "planned_distance_m",
            "max_planned_altitude_m",
            "planned_duration_sec",
            "estimated_energy_mah",
            "estimated_battery_used_percent",
            "required_battery_percent",
            "planning_time_ms",
            "waypoint_count",
            "failure_reason");

    @Override
    public String export(PlanningExperimentResult result) {
        if (result == null) throw new IllegalArgumentException("Planning experiment result is required.");
        StringBuilder csv = new StringBuilder();
        CsvFormatter.appendRecord(csv, HEADER);
        for (PlanningExperimentDatasetRow row : result.dataset()) {
            CsvFormatter.appendRecord(csv, values(result, row));
        }
        return csv.toString();
    }

    private List<String> values(PlanningExperimentResult result, PlanningExperimentDatasetRow row) {
        List<String> values = new ArrayList<>(HEADER.size());
        values.add(row.experimentId());
        values.add(row.scenarioId());
        values.add(scenarioLabel(result, row.scenarioId()));
        values.add(Double.toString(row.homeX()));
        values.add(Double.toString(row.homeY()));
        values.add(Double.toString(row.targetX()));
        values.add(Double.toString(row.targetY()));
        values.add(row.algorithm() == null ? null : row.algorithm().name());
        values.add(row.feasibilityStatus() == null ? null : row.feasibilityStatus().name());
        values.add(CsvFormatter.value(row.plannedDistanceM()));
        values.add(CsvFormatter.value(row.maxPlannedAltitudeM()));
        values.add(CsvFormatter.value(row.plannedDurationSec()));
        values.add(CsvFormatter.value(row.estimatedEnergyMah()));
        values.add(CsvFormatter.value(row.estimatedBatteryUsedPercent()));
        values.add(CsvFormatter.value(row.requiredBatteryPercent()));
        values.add(CsvFormatter.value(row.planningTimeMs()));
        values.add(CsvFormatter.value(row.waypointCount()));
        values.add(row.failureReason());
        return values;
    }

    private String scenarioLabel(PlanningExperimentResult result, String scenarioId) {
        return result.definition().scenarios().stream()
                .filter(scenario -> scenario.scenarioId().equals(scenarioId))
                .map(scenario -> scenario.label())
                .findFirst()
                .orElse(null);
    }

}
