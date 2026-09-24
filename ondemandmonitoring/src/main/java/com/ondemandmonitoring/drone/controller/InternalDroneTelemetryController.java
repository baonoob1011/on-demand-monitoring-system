package com.ondemandmonitoring.drone.controller;

import com.ondemandmonitoring.common.api.ApiResponse;
import com.ondemandmonitoring.common.exception.ApiException;
import com.ondemandmonitoring.common.exception.ErrorCode;
import com.ondemandmonitoring.drone.dto.request.TelemetryRequest;
import com.ondemandmonitoring.drone.service.IDroneTelemetryService;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/internal/v1/drone-telemetry")
@RequiredArgsConstructor
public class InternalDroneTelemetryController {

    private final IDroneTelemetryService droneTelemetryService;

    @Value("${DRONE_TELEMETRY_SECRET:}")
    private String configuredSecret;

    @PostMapping("/{droneCode}")
    public ResponseEntity<ApiResponse<Void>> receive(
            @PathVariable String droneCode,
            @RequestHeader(value = "X-Drone-Telemetry-Secret", required = false) String suppliedSecret,
            @Valid @RequestBody TelemetryRequest request) {
        if (configuredSecret == null || configuredSecret.isBlank()
                || suppliedSecret == null
                || !MessageDigest.isEqual(
                        configuredSecret.getBytes(StandardCharsets.UTF_8),
                        suppliedSecret.getBytes(StandardCharsets.UTF_8))) {
            throw new ApiException(ErrorCode.ACCESS_DENIED, "Drone telemetry credential is invalid");
        }
        droneTelemetryService.saveForRegisteredDrone(droneCode, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created("Telemetry recorded", null));
    }
}
