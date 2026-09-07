package com.ondemandmonitoring.device.controller;

import com.ondemandmonitoring.common.api.ApiResponse;
import com.ondemandmonitoring.device.domain.PreflightCheck;
import com.ondemandmonitoring.device.dto.response.PreflightCheckResponse;
import com.ondemandmonitoring.device.service.PreflightCheckService;
import com.ondemandmonitoring.device.mapper.PreflightCheckMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Device Diagnostics", description = "APIs for standalone drone preflight check diagnostics")
@RestController
@RequestMapping("/api/devices/{deviceCode}/preflight-checks")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class PreflightCheckController {

    PreflightCheckService preflightCheckService;
    PreflightCheckMapper preflightCheckMapper;

    @Operation(summary = "Run standalone preflight check", description = "Executes diagnostic checklist on a drone and returns check status")
    @PostMapping
    public ResponseEntity<ApiResponse<PreflightCheckResponse>> run(
            @PathVariable String deviceCode,
            @RequestParam(required = false) String missionId) {
        PreflightCheck preflightCheck = preflightCheckService.run(deviceCode, missionId);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.created("Preflight check completed", preflightCheckMapper.toResponse(preflightCheck)));
    }
}
