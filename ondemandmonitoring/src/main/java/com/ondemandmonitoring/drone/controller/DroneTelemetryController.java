package com.ondemandmonitoring.drone.controller;

import com.ondemandmonitoring.common.api.ApiResponse;
import com.ondemandmonitoring.drone.dto.request.TelemetryRequest;
import com.ondemandmonitoring.drone.domain.DroneTelemetry;
import com.ondemandmonitoring.drone.service.DroneTelemetryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Drone Telemetry", description = "APIs for receiving live telemetry data sent by PX4 / MAVSDK drone sensors")
@RestController
@RequestMapping("/api/drones/{droneCode}/telemetry")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class DroneTelemetryController {

    DroneTelemetryService droneTelemetryService;
    
    @Operation(summary = "Receive drone telemetry", description = "Persists live telemetry snapshot (battery, GPS fix, sensors, altitude, speed) sent from drone")
    @PostMapping
    public ResponseEntity<ApiResponse<DroneTelemetry>> create(
            @PathVariable String droneCode,
            @Valid @RequestBody TelemetryRequest request) {
        DroneTelemetry telemetry = droneTelemetryService.save(droneCode, request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.created("Telemetry created", telemetry));
    }
}
