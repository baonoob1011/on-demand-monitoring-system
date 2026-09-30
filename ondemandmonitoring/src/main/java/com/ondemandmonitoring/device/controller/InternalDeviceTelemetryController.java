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
@RequestMapping("/api/internal/v1/drone-telemetry")
@RequiredArgsConstructor
public class InternalDeviceTelemetryController {

    private final IDeviceTelemetryService telemetryService;

    @Value("${DRONE_TELEMETRY_SECRET:}")
    private String configuredSecret;

    @PostMapping("/{deviceId}")
    public ResponseEntity<ApiResponse<Void>> receive(
            @PathVariable String deviceId,
            @RequestHeader(value = "X-Drone-Telemetry-Secret", required = false) String suppliedSecret,
            @Valid @RequestBody TelemetryRequest request) {
        verifyCredential(suppliedSecret);
        telemetryService.record(deviceId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created("Telemetry recorded", null));
    }

    private void verifyCredential(String suppliedSecret) {
        if (configuredSecret == null || configuredSecret.isBlank()
                || suppliedSecret == null
                || !MessageDigest.isEqual(
                        configuredSecret.getBytes(StandardCharsets.UTF_8),
                        suppliedSecret.getBytes(StandardCharsets.UTF_8))) {
            throw new ApiException(ErrorCode.ACCESS_DENIED, "Device telemetry credential is invalid");
        }
    }
}
