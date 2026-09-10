package com.ondemandmonitoring.zone.controller;

import com.ondemandmonitoring.common.api.ApiResponse;
import com.ondemandmonitoring.zone.dto.response.SimulationMapFeatureResponse;
import com.ondemandmonitoring.zone.service.ISimulationMapFeatureService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Simulation Map", description = "Local-meter geometry features for the current PX4/Gazebo world")
@RestController
@RequestMapping("/api/simulation-map")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class SimulationMapFeatureController {

    ISimulationMapFeatureService simulationMapFeatureService;

    @Operation(summary = "List simulation map features", description = "Returns map background geometry in local simulation meters")
    @GetMapping
    public ResponseEntity<ApiResponse<List<SimulationMapFeatureResponse>>> findAll() {
        return ResponseEntity.ok(ApiResponse.ok(simulationMapFeatureService.findAll()));
    }
}
