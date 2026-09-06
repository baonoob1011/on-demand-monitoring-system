package com.ondemandmonitoring.device.controller;

import com.ondemandmonitoring.common.api.ApiResponse;
import com.ondemandmonitoring.device.domain.PreflightCheck;
import com.ondemandmonitoring.device.dto.response.PreflightCheckResponse;
import com.ondemandmonitoring.device.service.PreflightCheckService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/devices/{deviceCode}/preflight-checks")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class PreflightCheckController {

    PreflightCheckService preflightCheckService;

    @PostMapping
    public ResponseEntity<ApiResponse<PreflightCheckResponse>> run(@PathVariable String deviceCode) {
        PreflightCheck preflightCheck = preflightCheckService.run(deviceCode);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.created("Preflight check completed", PreflightCheckResponse.from(preflightCheck)));
    }
}
