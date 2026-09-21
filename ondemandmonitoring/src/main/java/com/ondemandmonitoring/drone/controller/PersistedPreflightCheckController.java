package com.ondemandmonitoring.drone.controller;

import com.ondemandmonitoring.common.api.ApiResponse;
import com.ondemandmonitoring.drone.dto.request.PreflightItemUpdateRequest;
import com.ondemandmonitoring.drone.dto.response.PersistedPreflightCheckResponse;
import com.ondemandmonitoring.drone.service.PersistedPreflightCheckService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class PersistedPreflightCheckController {

    private final PersistedPreflightCheckService service;

    @PostMapping("/api/missions/{missionId}/preflight-checks")
    public ResponseEntity<ApiResponse<PersistedPreflightCheckResponse>> start(
            @PathVariable String missionId) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.created(service.start(missionId)));
    }

    @GetMapping("/api/missions/{missionId}/preflight-checks")
    public ApiResponse<List<PersistedPreflightCheckResponse>> history(
            @PathVariable String missionId) {
        return ApiResponse.ok(service.history(missionId));
    }

    @GetMapping("/api/missions/{missionId}/preflight-checks/current")
    public ApiResponse<PersistedPreflightCheckResponse> current(
            @PathVariable String missionId) {
        return ApiResponse.ok(service.current(missionId));
    }

    @GetMapping("/api/preflight-checks/{id}")
    public ApiResponse<PersistedPreflightCheckResponse> get(
            @PathVariable String id) {
        return ApiResponse.ok(service.get(id));
    }

    @PatchMapping("/api/preflight-checks/{id}/items/{checkType}")
    public ApiResponse<PersistedPreflightCheckResponse> update(
            @PathVariable String id,
            @PathVariable String checkType,
            @Valid @RequestBody PreflightItemUpdateRequest request) {
        return ApiResponse.ok(service.update(id, checkType, request));
    }
}
