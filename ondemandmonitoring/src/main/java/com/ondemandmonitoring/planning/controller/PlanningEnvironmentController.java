package com.ondemandmonitoring.planning.controller;

import com.ondemandmonitoring.common.api.ApiResponse;
import com.ondemandmonitoring.planning.domain.PlanningGrid;
import com.ondemandmonitoring.planning.dto.response.EnvironmentSample;
import com.ondemandmonitoring.planning.dto.response.PlanningGridMetadataResponse;
import com.ondemandmonitoring.planning.service.PlanningEnvironment;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Planning Environment", description = "Read-only Gazebo XY planning-map debug endpoints")
@RestController
@RequestMapping("/api/planning/environment")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class PlanningEnvironmentController {

    PlanningEnvironment planningEnvironment;

    @Operation(summary = "Sample planning environment", description = "Returns terrain, surface, obstacle, and runtime restricted-zone data for a Gazebo XY point")
    @GetMapping("/sample")
    public ResponseEntity<ApiResponse<EnvironmentSample>> sample(
            @RequestParam("x") double simX,
            @RequestParam("y") double simY) {
        return ResponseEntity.ok(ApiResponse.ok(planningEnvironment.sample(simX, simY)));
    }

    @Operation(summary = "Planning grid metadata", description = "Returns bounds and resolution for the loaded planning grid")
    @GetMapping("/metadata")
    public ResponseEntity<ApiResponse<PlanningGridMetadataResponse>> metadata() {
        PlanningGrid grid = planningEnvironment.grid();
        PlanningGrid.Bounds bounds = grid.bounds();
        return ResponseEntity.ok(ApiResponse.ok(new PlanningGridMetadataResponse(
                grid.world(),
                grid.coordinateSystem(),
                bounds.minX(),
                bounds.maxX(),
                bounds.minY(),
                bounds.maxY(),
                grid.resolutionM(),
                grid.width(),
                grid.height(),
                grid.lookup())));
    }

    @Operation(summary = "Planning grid debug data", description = "Returns the loaded planning grid for debug visualization only")
    @GetMapping("/grid")
    public ResponseEntity<ApiResponse<PlanningGrid>> grid() {
        return ResponseEntity.ok(ApiResponse.ok(planningEnvironment.grid()));
    }
}
