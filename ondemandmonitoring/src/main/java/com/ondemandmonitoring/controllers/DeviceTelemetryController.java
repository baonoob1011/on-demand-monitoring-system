package com.ondemandmonitoring.controllers;

import com.ondemandmonitoring.common.api.ApiResponse;
import com.ondemandmonitoring.dto.request.TelemetryRequest;
import com.ondemandmonitoring.entities.DeviceTelemetry;
import com.ondemandmonitoring.services.DeviceTelemetryService;
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

@RestController
@RequestMapping("/api/devices/{deviceCode}/telemetry")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class DeviceTelemetryController {

    DeviceTelemetryService deviceTelemetryService;

    @PostMapping
    public ResponseEntity<ApiResponse<DeviceTelemetry>> create(
            @PathVariable String deviceCode,
            @Valid @RequestBody TelemetryRequest request) {
        DeviceTelemetry telemetry = deviceTelemetryService.save(deviceCode, request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.created("Telemetry created", telemetry));
    }
}
