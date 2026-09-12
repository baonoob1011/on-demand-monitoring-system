package com.ondemandmonitoring.zone.controller;

import com.ondemandmonitoring.common.api.ApiResponse;
import com.ondemandmonitoring.zone.dto.request.ZoneCreateRequest;
import com.ondemandmonitoring.zone.dto.request.ZonePolygonUpdateRequest;
import com.ondemandmonitoring.zone.dto.response.ZoneResponse;
import com.ondemandmonitoring.zone.service.IZoneService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Simulation Zones", description = "PostGIS polygons derived from the current PX4/Gazebo simulation map")
@RestController
@RequestMapping("/api/zones")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ZoneController {

    IZoneService zoneService;

    @Operation(summary = "List simulation zones", description = "Returns local simulation-meter polygons for each monitoring region")
    @GetMapping
    public ResponseEntity<ApiResponse<List<ZoneResponse>>> findAll() {
        return ResponseEntity.ok(ApiResponse.ok(zoneService.findAll()));
    }

    @Operation(summary = "Create simulation zone", description = "Creates and persists a new editable simulation zone")
    @PostMapping
    public ResponseEntity<ApiResponse<ZoneResponse>> create(
            @Valid @RequestBody ZoneCreateRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Zone created", zoneService.create(request)));
    }

    @Operation(summary = "Update simulation zone polygon", description = "Persists edited local simulation-meter polygon coordinates")
    @PutMapping("/{id}/polygon")
    public ResponseEntity<ApiResponse<ZoneResponse>> updatePolygon(
            @PathVariable String id,
            @Valid @RequestBody ZonePolygonUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Zone polygon saved", zoneService.updatePolygon(id, request)));
    }

    @Operation(summary = "Delete simulation zone", description = "Deletes a selected simulation zone from the database")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable String id) {
        zoneService.delete(id);
        return ResponseEntity.ok(ApiResponse.ok("Zone deleted", null));
    }
}
