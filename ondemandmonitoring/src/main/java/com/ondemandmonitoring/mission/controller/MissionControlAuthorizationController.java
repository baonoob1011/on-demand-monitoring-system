package com.ondemandmonitoring.mission.controller;

import com.ondemandmonitoring.common.api.ApiResponse;
import com.ondemandmonitoring.mission.dto.response.MissionControlContextResponse;
import com.ondemandmonitoring.mission.service.IMissionControlAuthorizationService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/missions")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('DRONE_OPERATOR', 'SYSTEM_OPERATOR', 'ADMIN')")
public class MissionControlAuthorizationController {

    private final IMissionControlAuthorizationService authorizationService;

    @GetMapping("/{missionId}/control-context")
    public ApiResponse<MissionControlContextResponse> authorize(
            @PathVariable String missionId,
            @RequestParam(required = false) String droneId) {
        return ApiResponse.ok(authorizationService.authorize(missionId, droneId));
    }
}
