package com.ondemandmonitoring.zone.controller;

import com.ondemandmonitoring.common.api.ApiResponse;
import com.ondemandmonitoring.zone.dto.request.ThermalSourceRequest;
import com.ondemandmonitoring.zone.dto.response.ThermalSourceResponse;
import com.ondemandmonitoring.zone.service.IThermalSourceService;
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
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Simulation Thermal Sources", description = "Thermal configuration in local Gazebo XY meters")
@RestController
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ThermalSourceController {

    IThermalSourceService thermalSourceService;

    @Operation(summary = "List thermal sources")
    @GetMapping("/api/thermal-sources")
    public ResponseEntity<ApiResponse<List<ThermalSourceResponse>>> findAll() {
        return ResponseEntity.ok(ApiResponse.ok(thermalSourceService.findAll()));
    }

    @Operation(summary = "List thermal sources for a simulation zone")
    @GetMapping("/api/zones/{zoneCode}/thermal-sources")
    public ResponseEntity<ApiResponse<List<ThermalSourceResponse>>> findByZoneCode(
            @PathVariable String zoneCode) {
        return ResponseEntity.ok(ApiResponse.ok(thermalSourceService.findByZoneCode(zoneCode)));
    }

    @Operation(summary = "Create thermal source")
    @PostMapping("/api/thermal-sources")
    public ResponseEntity<ApiResponse<ThermalSourceResponse>> create(
            @Valid @RequestBody ThermalSourceRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Thermal source created", thermalSourceService.create(request)));
    }

    @Operation(summary = "Update thermal source")
    @PutMapping("/api/thermal-sources/{id}")
    public ResponseEntity<ApiResponse<ThermalSourceResponse>> update(
            @PathVariable String id,
            @Valid @RequestBody ThermalSourceRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Thermal source saved", thermalSourceService.update(id, request)));
    }

    @Operation(summary = "Delete thermal source")
    @DeleteMapping("/api/thermal-sources/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable String id) {
        thermalSourceService.delete(id);
        return ResponseEntity.ok(ApiResponse.ok("Thermal source deleted", null));
    }
}
