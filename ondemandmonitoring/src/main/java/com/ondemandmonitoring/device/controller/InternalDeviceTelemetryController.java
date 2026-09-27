package com.ondemandmonitoring.device.controller;

import com.ondemandmonitoring.common.api.ApiResponse;
import com.ondemandmonitoring.common.exception.ApiException;
import com.ondemandmonitoring.common.exception.ErrorCode;
import com.ondemandmonitoring.device.dto.request.TelemetryRequest;
import com.ondemandmonitoring.device.service.IDeviceTelemetryService;
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
@RequestMapping({"/api/internal/v1/device-telemetry", "/api/internal/v1/drone-telemetry"})
@RequiredArgsConstructor
public class InternalDeviceTelemetryController {

    private final IDeviceTelemetryService deviceTelemetryService;

    @Value("${DEVICE_TELEMETRY_SECRET:${DRONE_TELEMETRY_SECRET:}}")
    private String configuredSecret;

    @PostMapping("/{deviceCode}")
    public ResponseEntity<ApiResponse<Void>> receive(
            @PathVariable String deviceCode,
            @RequestHeader(value = "X-Device-Telemetry-Secret", required = false) String suppliedSecret,
            @RequestHeader(value = "X-Drone-Telemetry-Secret", required = false) String legacySuppliedSecret,
            @Valid @RequestBody TelemetryRequest request) {
        String effectiveSecret = suppliedSecret != null ? suppliedSecret : legacySuppliedSecret;
        if (configuredSecret == null || configuredSecret.isBlank()
                || effectiveSecret == null
                || !MessageDigest.isEqual(
                        configuredSecret.getBytes(StandardCharsets.UTF_8),
                        effectiveSecret.getBytes(StandardCharsets.UTF_8))) {
            throw new ApiException(ErrorCode.ACCESS_DENIED, "Device telemetry credential is invalid");
        }
        deviceTelemetryService.saveForRegisteredDrone(deviceCode, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created("Telemetry recorded", null));
    }
}
