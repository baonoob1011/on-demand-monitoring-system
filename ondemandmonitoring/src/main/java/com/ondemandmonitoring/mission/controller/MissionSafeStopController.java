package com.ondemandmonitoring.mission.controller;

import com.ondemandmonitoring.common.api.ApiResponse;
import com.ondemandmonitoring.mission.dto.request.*;
import com.ondemandmonitoring.mission.dto.response.MissionSafeStopResponse;
import com.ondemandmonitoring.mission.service.IMissionSafeStopService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;

@RestController @RequiredArgsConstructor @RequestMapping("/api/missions/{missionId}/safe-stop")
public class MissionSafeStopController {
    private final IMissionSafeStopService service;
    @GetMapping @PreAuthorize("@missionAuthorizationService.canViewMission(#missionId)")
    public ApiResponse<MissionSafeStopResponse> get(@PathVariable String missionId) { return ApiResponse.ok(service.get(missionId)); }
    @PostMapping @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public ApiResponse<MissionSafeStopResponse> request(@PathVariable String missionId, @Valid @RequestBody MissionSafeStopRequest request) { return ApiResponse.ok(service.request(missionId, request)); }
    @PostMapping("/confirm") @PreAuthorize("hasRole('STAFF')")
    public ApiResponse<MissionSafeStopResponse> confirm(@PathVariable String missionId, @Valid @RequestBody MissionSafeStopConfirmationRequest request) { return ApiResponse.ok(service.confirm(missionId, request)); }
    @PostMapping("/cleanup") @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public ApiResponse<MissionSafeStopResponse> cleanup(@PathVariable String missionId) { return ApiResponse.ok(service.cleanup(missionId)); }
    @PostMapping("/fail") @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public ApiResponse<MissionSafeStopResponse> fail(@PathVariable String missionId, @RequestBody @Valid MissionSafeStopFailureRequest request) { return ApiResponse.ok(service.fail(missionId, request.reason())); }
}
