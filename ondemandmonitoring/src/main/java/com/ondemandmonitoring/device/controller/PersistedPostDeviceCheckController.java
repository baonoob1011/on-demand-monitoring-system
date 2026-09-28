package com.ondemandmonitoring.device.controller;

import com.ondemandmonitoring.common.api.ApiResponse;
import com.ondemandmonitoring.device.dto.request.DeviceCheckItemUpdateRequest;
import com.ondemandmonitoring.device.dto.response.PersistedPostDeviceCheckResponse;
import com.ondemandmonitoring.device.service.IPersistedPostDeviceCheckService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class PersistedPostDeviceCheckController {

    private final IPersistedPostDeviceCheckService service;

    @PostMapping("/api/missions/{missionId}/post-device-checks")
    @PreAuthorize("hasRole('DRONE_OPERATOR') and @missionAuthorizationService.isAssignedOperator(#missionId)")
    public ResponseEntity<ApiResponse<PersistedPostDeviceCheckResponse>> start(
            @PathVariable String missionId) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.created(service.start(missionId)));
    }

    @GetMapping("/api/missions/{missionId}/post-device-checks")
    @PreAuthorize("hasAnyRole('DRONE_OPERATOR', 'SYSTEM_OPERATOR', 'ADMIN') or @missionAuthorizationService.isAssignedOperator(#missionId)")
    public ApiResponse<List<PersistedPostDeviceCheckResponse>> history(
            @PathVariable String missionId) {
        return ApiResponse.ok(service.history(missionId));
    }

    @GetMapping("/api/missions/{missionId}/post-device-checks/current")
    @PreAuthorize("hasAnyRole('DRONE_OPERATOR', 'SYSTEM_OPERATOR', 'ADMIN') or @missionAuthorizationService.isAssignedOperator(#missionId)")
    public ApiResponse<PersistedPostDeviceCheckResponse> current(
            @PathVariable String missionId) {
        return ApiResponse.ok(service.current(missionId));
    }

    @GetMapping("/api/post-device-checks/{id}")
    @PreAuthorize("hasAnyRole('DRONE_OPERATOR', 'SYSTEM_OPERATOR', 'ADMIN')")
    public ApiResponse<PersistedPostDeviceCheckResponse> get(
            @PathVariable String id) {
        return ApiResponse.ok(service.get(id));
    }

    @PatchMapping("/api/post-device-checks/{id}/items/{checkType}")
    @PreAuthorize("hasRole('DRONE_OPERATOR')")
    public ApiResponse<PersistedPostDeviceCheckResponse> update(
            @PathVariable String id,
            @PathVariable String checkType,
            @Valid @RequestBody DeviceCheckItemUpdateRequest request) {
        return ApiResponse.ok(service.update(id, checkType, request));
    }
}


