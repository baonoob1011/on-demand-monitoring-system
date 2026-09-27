package com.ondemandmonitoring.missionv2.controller;

import com.ondemandmonitoring.common.api.ApiResponse;
import com.ondemandmonitoring.mission.dto.response.MissionResponse;
import com.ondemandmonitoring.missionv2.dto.request.AssignDeviceRequest;
import com.ondemandmonitoring.missionv2.dto.request.AssignStaffRequest;
import com.ondemandmonitoring.missionv2.dto.request.MissionCreateRequest;
import com.ondemandmonitoring.missionv2.dto.request.MissionUpdateRequest;
import com.ondemandmonitoring.missionv2.dto.response.MissionV2Response;
import com.ondemandmonitoring.missionv2.service.IMissionV2Service;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * REST entry point for Mission Lifecycle V2 APIs in module missionv2.
 *
 * Base path: /api/v2/missions
 */
@Tag(name = "Mission Operations V2", description = "V2 APIs for Mission lifecycle and management")
@RestController
@RequestMapping("/api/v2/missions")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@PreAuthorize("hasAnyRole('DRONE_OPERATOR', 'STAFF', 'SYSTEM_OPERATOR', 'ADMIN')")
public class MissionV2Controller {

    IMissionV2Service missionV2Service;

    /**
     * POST /api/v2/missions
     * Create a new mission. Must specify orderId.
     */
    @PostMapping
    public ResponseEntity<ApiResponse<MissionV2Response>> createMission(
            @Valid @RequestBody MissionCreateRequest request) {
        MissionV2Response response = missionV2Service.createMission(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created("Mission created successfully", response));
    }

    /**
     * PUT /api/v2/missions/{id}
     * Update mission schedule dates or preferred time.
     */
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<MissionV2Response>> updateMission(
            @PathVariable String id,
            @Valid @RequestBody MissionUpdateRequest request) {
        MissionV2Response response = missionV2Service.updateMission(id, request);
        return ResponseEntity.ok(ApiResponse.ok("Mission updated successfully", response));
    }

    /**
     * POST /api/v2/missions/{id}/assign-device
     * Manager assigns Device to the mission.
     */
    @PostMapping("/{id}/assign-device")
    @PreAuthorize("hasAnyRole('STAFF', 'SYSTEM_OPERATOR', 'ADMIN')")
    public ResponseEntity<ApiResponse<MissionV2Response>> assignDevice(
            @PathVariable String id,
            @Valid @RequestBody AssignDeviceRequest request) {
        MissionV2Response response = missionV2Service.assignDevice(id, request);
        return ResponseEntity.ok(ApiResponse.ok("Đã gán Device thành công", response));
    }

    /**
     * POST /api/v2/missions/{id}/assign-staff
     * Manager assigns Staff to the mission.
     */
    @PostMapping("/{id}/assign-staff")
    @PreAuthorize("hasAnyRole('STAFF', 'SYSTEM_OPERATOR', 'ADMIN')")
    public ResponseEntity<ApiResponse<MissionV2Response>> assignStaff(
            @PathVariable String id,
            @Valid @RequestBody AssignStaffRequest request) {
        MissionV2Response response = missionV2Service.assignStaff(id, request);
        return ResponseEntity.ok(ApiResponse.ok("Đã gán Staff thành công", response));
    }
}
