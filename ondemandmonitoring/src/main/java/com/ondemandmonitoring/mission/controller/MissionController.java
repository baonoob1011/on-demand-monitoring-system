package com.ondemandmonitoring.mission.controller;

import com.ondemandmonitoring.common.api.ApiResponse;
import com.ondemandmonitoring.mission.domain.Mission;
import com.ondemandmonitoring.mission.service.MissionService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/missions")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class MissionController {

    MissionService missionService;

    @GetMapping("/next")
    public ResponseEntity<ApiResponse<Mission>> nextMission(@RequestParam String deviceCode) {
        Mission mission = missionService.dispatchNextMission(deviceCode);
        return ResponseEntity.ok(ApiResponse.ok("Mission dispatched", mission));
    }
}
