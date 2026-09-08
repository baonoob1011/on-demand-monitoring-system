package com.ondemandmonitoring.mission.controller;

import com.ondemandmonitoring.common.api.ApiResponse;
import com.ondemandmonitoring.device.domain.DeviceImage;
import com.ondemandmonitoring.device.dto.response.DeviceImageResponse;
import com.ondemandmonitoring.device.dto.response.PreflightCheckResponse;
import com.ondemandmonitoring.mission.domain.Mission;
import com.ondemandmonitoring.mission.dto.request.DroneReplacementRequest;
import com.ondemandmonitoring.mission.dto.request.MissionFailRequest;
import com.ondemandmonitoring.mission.dto.request.MissionRejectRequest;
import com.ondemandmonitoring.mission.dto.request.PostFlightStatusRequest;
import com.ondemandmonitoring.mission.dto.response.MissionResponse;
import com.ondemandmonitoring.mission.mapper.MissionMapper;
import com.ondemandmonitoring.device.mapper.DeviceImageMapper;
import com.ondemandmonitoring.mission.service.IMissionMediaUploadService;
import com.ondemandmonitoring.mission.service.IMissionService;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * REST entry point for all Flow 3 (Drone Operator) mission lifecycle use cases.
 *
 * Base path: /api/missions
 */
@Tag(name = "Mission Operations", description = "APIs for Drone Operator mission lifecycle, GCS pairing, preflight checks, and flight execution")
@RestController
@RequestMapping("/api/missions")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class MissionController {

    IMissionService missionService;
    IMissionMediaUploadService missionMediaUploadService;
    MissionMapper missionMapper;
    DeviceImageMapper deviceImageMapper;

    /** GET /api/missions/{id} – retrieve mission details */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<MissionResponse>> getById(@PathVariable String id) {
        Mission mission = missionService.findById(id);
        return ResponseEntity.ok(ApiResponse.ok(missionMapper.toResponse(mission)));
    }

    // ------------------------------------------------------------------
    // F3.1 – Operator acceptance
    // ------------------------------------------------------------------

    /**
     * PATCH /api/missions/{id}/accept
     * Drone Operator confirms they accept the mission.
     * Transitions: WAITING_OPERATOR_ACCEPTANCE → SCHEDULED
     */
    @PatchMapping("/{id}/accept")
    public ResponseEntity<ApiResponse<MissionResponse>> accept(
            @PathVariable String id,
            @RequestHeader("X-Operator-Id") String operatorId) {
        Mission mission = missionService.acceptMission(id, operatorId);
        return ResponseEntity.ok(ApiResponse.ok("Mission đã được chấp nhận", missionMapper.toResponse(mission)));
    }

    /**
     * PATCH /api/missions/{id}/reject
     * Drone Operator rejects the mission with a mandatory reason.
     * Transitions: WAITING_OPERATOR_ACCEPTANCE → RESOURCE_ASSIGNING
     */
    @PatchMapping("/{id}/reject")
    public ResponseEntity<ApiResponse<MissionResponse>> reject(
            @PathVariable String id,
            @RequestHeader("X-Operator-Id") String operatorId,
            @Valid @RequestBody MissionRejectRequest request) {
        Mission mission = missionService.rejectMission(id, operatorId, request.getReason());
        return ResponseEntity.ok(ApiResponse.ok("Mission đã bị từ chối, hệ thống sẽ phân công lại", missionMapper.toResponse(mission)));
    }

    // ------------------------------------------------------------------
    // F3.2 – Pre-flight check & drone replacement
    // ------------------------------------------------------------------

    /**
     * POST /api/missions/{id}/connect
     * Confirm telemetry link with GCS app (powerOnAndPairWithGCSApp).
     * Transitions: SCHEDULED -> CONNECTED
     */
    @PostMapping("/{id}/connect")
    public ResponseEntity<ApiResponse<MissionResponse>> connectGcs(@PathVariable String id) {
        Mission mission = missionService.connectGcs(id);
        return ResponseEntity.ok(ApiResponse.ok("Telemetry link with GCS confirmed (CONNECTED)", missionMapper.toResponse(mission)));
    }

    /**
     * POST /api/missions/{id}/preflight-check?deviceCode=DRONE-01
     * Runs digital preflight checklist (Battery >= 80%, GPS >= 8 sats, Camera/Gimbal, Storage, Weather).
     */
    @PostMapping("/{id}/preflight-check")
    public ResponseEntity<ApiResponse<PreflightCheckResponse>> runPreflightCheck(
            @PathVariable String id,
            @RequestParam String deviceCode) {
        PreflightCheckResponse response = missionService.runPreflightCheck(id, deviceCode);
        boolean passed = Boolean.TRUE.equals(response.getOverallPassed());
        return ResponseEntity
                .status(passed ? HttpStatus.OK : HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ApiResponse.ok(
                        passed ? "Digital preflight check PASSED – Flight Access Token issued"
                                : "Digital preflight check FAILED – fault classified: " + response.getFaultType(),
                        response));
    }

    /**
     * PATCH /api/missions/{id}/replace-drone
     * Operator selects a replacement drone when pre-flight fails.
     */
    @PatchMapping("/{id}/replace-drone")
    public ResponseEntity<ApiResponse<MissionResponse>> replaceDrone(
            @PathVariable String id,
            @Valid @RequestBody DroneReplacementRequest request) {
        Mission mission = missionService.replaceDrone(id, request.getNewDeviceCode());
        return ResponseEntity.ok(ApiResponse.ok("Drone successfully replaced", missionMapper.toResponse(mission)));
    }

    // ------------------------------------------------------------------
    // F3.2b – Bàn giao quyền điều khiển (Handover of control)
    // ------------------------------------------------------------------

    /**
     * POST /api/missions/{id}/handover
     * Operator formally accepts control of the drone console.
     */
    @PostMapping("/{id}/handover")
    public ResponseEntity<ApiResponse<MissionResponse>> handover(
            @PathVariable String id,
            @RequestHeader("X-Operator-Id") String operatorId) {
        Mission mission = missionService.handoverControl(id, operatorId);
        return ResponseEntity.ok(ApiResponse.ok("Control handed over to operator " + operatorId, missionMapper.toResponse(mission)));
    }

    // ------------------------------------------------------------------
    // F3.3 – Mission execution lifecycle
    // ------------------------------------------------------------------

    /**
     * POST /api/missions/{id}/start
     * Operator clicks "Press Takeoff". Validates Flight Access Token.
     */
    @PostMapping("/{id}/start")
    public ResponseEntity<ApiResponse<MissionResponse>> start(
            @PathVariable String id,
            @RequestParam(required = false) String tokenValue) {
        Mission mission = missionService.startMission(id, tokenValue);
        return ResponseEntity.ok(ApiResponse.ok("Takeoff confirmed – mission IN_FLIGHT", missionMapper.toResponse(mission)));
    }

    /** POST /api/missions/{id}/return – drone heads back, RETURNING */
    @PostMapping("/{id}/return")
    public ResponseEntity<ApiResponse<MissionResponse>> markReturning(@PathVariable String id) {
        Mission mission = missionService.markReturning(id);
        return ResponseEntity.ok(ApiResponse.ok("Drone đang quay trở về", missionMapper.toResponse(mission)));
    }

    /** POST /api/missions/{id}/postflight – drone landed, POSTFLIGHT_CHECKING */
    @PostMapping("/{id}/postflight")
    public ResponseEntity<ApiResponse<MissionResponse>> startPostflight(@PathVariable String id) {
        Mission mission = missionService.startPostflightChecking(id);
        return ResponseEntity.ok(ApiResponse.ok("Bắt đầu kiểm tra sau bay", missionMapper.toResponse(mission)));
    }

    /** POST /api/missions/{id}/complete – operator confirms complete */
    @PostMapping("/{id}/complete")
    public ResponseEntity<ApiResponse<MissionResponse>> complete(@PathVariable String id) {
        Mission mission = missionService.completeMission(id);
        return ResponseEntity.ok(ApiResponse.ok("Mission đã hoàn thành", missionMapper.toResponse(mission)));
    }

    /** POST /api/missions/{id}/fail – operator reports mission failure */
    @PostMapping("/{id}/fail")
    public ResponseEntity<ApiResponse<MissionResponse>> fail(
            @PathVariable String id,
            @Valid @RequestBody MissionFailRequest request) {
        Mission mission = missionService.failMission(id, request.getReason());
        return ResponseEntity.ok(ApiResponse.ok("Mission đã báo lỗi thất bại", missionMapper.toResponse(mission)));
    }

    // ------------------------------------------------------------------
    // F3.4 – Media Upload (with retry & notification on failure)
    // ------------------------------------------------------------------

    /**
     * POST /api/missions/{id}/media (Multipart)
     */
    @PostMapping(value = "/{id}/media", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<DeviceImageResponse>> uploadMedia(
            @PathVariable String id,
            @RequestParam String deviceCode,
            @RequestParam("file") MultipartFile file) {
        DeviceImage saved = missionMediaUploadService.uploadWithRetry(id, deviceCode, file);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Tải media thành công", deviceImageMapper.toResponse(saved)));
    }

    // ------------------------------------------------------------------
    // F3.5 – Post-flight status update
    // ------------------------------------------------------------------

    /**
     * PATCH /api/missions/{id}/postflight-status
     */
    @PatchMapping("/{id}/postflight-status")
    public ResponseEntity<ApiResponse<MissionResponse>> updatePostFlightStatus(
            @PathVariable String id,
            @Valid @RequestBody PostFlightStatusRequest request) {
        Mission mission = missionService.updatePostFlightStatus(id, request.getNewDeviceStatus(), request.getNotes());
        return ResponseEntity.ok(ApiResponse.ok("Cập nhật trạng thái drone sau bay thành công", missionMapper.toResponse(mission)));
    }
}
