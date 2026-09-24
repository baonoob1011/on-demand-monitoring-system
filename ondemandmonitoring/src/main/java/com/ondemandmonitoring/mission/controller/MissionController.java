package com.ondemandmonitoring.mission.controller;

import com.ondemandmonitoring.common.api.ApiResponse;
import com.ondemandmonitoring.drone.dto.response.PreflightCheckResponse;
import com.ondemandmonitoring.media.domain.MediaAsset;
import com.ondemandmonitoring.media.dto.response.MediaAssetResponse;
import com.ondemandmonitoring.media.mapper.MediaAssetMapper;
import com.ondemandmonitoring.mission.dto.request.DroneReplacementRequest;
import com.ondemandmonitoring.mission.dto.request.MissionFailRequest;
import com.ondemandmonitoring.mission.dto.request.MissionRejectRequest;
import com.ondemandmonitoring.mission.dto.request.MissionResourceAssignmentRequest;
import com.ondemandmonitoring.mission.dto.request.PostFlightStatusRequest;
import com.ondemandmonitoring.mission.dto.response.MissionPlanResponse;
import com.ondemandmonitoring.mission.dto.response.MissionResponse;
import com.ondemandmonitoring.mission.dto.response.MissionTelemetryReadinessResponse;
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
import org.springframework.security.access.prepost.PreAuthorize;

import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;

/**
 * REST entry point for all Flow 3 (Drone Operator) mission lifecycle use cases.
 * Enterprise pattern: Thin controller receiving DTOs directly from Service layer.
 *
 * Base path: /api/missions
 */
@Tag(name = "Mission Operations (Flow 3)", description = "APIs for Drone Operator mission lifecycle, GCS pairing, preflight checks, and flight execution")
@RestController
@RequestMapping("/api/missions")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@PreAuthorize("hasAnyRole('DRONE_OPERATOR', 'STAFF', 'SYSTEM_OPERATOR', 'ADMIN')")
public class MissionController {

    IMissionService missionService;
    IMissionMediaUploadService missionMediaUploadService;
    MediaAssetMapper mediaAssetMapper;

    // ------------------------------------------------------------------
    // Query
    // ------------------------------------------------------------------

    /**
     * GET /api/missions?operatorId={id}
     * List all missions assigned to a drone operator, sorted by scheduledStartAt desc.
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('STAFF', 'SYSTEM_OPERATOR', 'ADMIN')")
    public ResponseEntity<ApiResponse<List<MissionResponse>>> listByOperator(
            @RequestParam String operatorId) {
        List<MissionResponse> missions = missionService.getByOperatorId(operatorId);
        return ResponseEntity.ok(ApiResponse.ok(missions));
    }

    @PreAuthorize("hasRole('DRONE_OPERATOR')")
    @GetMapping("/mine")
    public ResponseEntity<ApiResponse<List<MissionResponse>>> listCurrentOperatorMissions() {
        return ResponseEntity.ok(ApiResponse.ok(missionService.getCurrentOperatorMissions()));
    }

    /** GET /api/missions/code/{missionCode} – retrieve mission details by code */
    @GetMapping("/code/{missionCode}")
    @PreAuthorize("hasAnyRole('STAFF', 'SYSTEM_OPERATOR', 'ADMIN')")
    public ResponseEntity<ApiResponse<MissionResponse>> getByCode(@PathVariable String missionCode) {
        MissionResponse response = missionService.getByCodeResponse(missionCode);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    /** GET /api/missions/{id} – retrieve mission details */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('STAFF', 'SYSTEM_OPERATOR', 'ADMIN') or @missionAuthorizationService.isAssignedOperator(#id)")
    public ResponseEntity<ApiResponse<MissionResponse>> getById(@PathVariable String id) {
        MissionResponse response = missionService.getByIdResponse(id);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    /** GET /api/missions/{id}/plan – retrieve generated operational MissionPlan */
    @GetMapping("/{id}/plan")
    @PreAuthorize("hasAnyRole('STAFF', 'SYSTEM_OPERATOR', 'ADMIN') or @missionAuthorizationService.isAssignedOperator(#id)")
    public ResponseEntity<ApiResponse<MissionPlanResponse>> getPlan(@PathVariable String id) {
        MissionPlanResponse response = missionService.getMissionPlan(id);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    // ------------------------------------------------------------------
    @GetMapping("/pending-assignment")
    @PreAuthorize("hasAnyRole('STAFF', 'SYSTEM_OPERATOR', 'ADMIN')")
    public ResponseEntity<ApiResponse<List<MissionResponse>>> getPendingAssignment() {
        return ResponseEntity.ok(ApiResponse.ok(missionService.getPendingAssignmentMissions()));
    }

    // F2 – Manager Assignment (Flow 2)
    // ------------------------------------------------------------------

    /**
     * POST /api/missions/{id}/assign-drone
     * Manager assigns Drone to the mission.
     */
    @PostMapping("/{id}/assign-drone")
    @PreAuthorize("hasAnyRole('STAFF', 'SYSTEM_OPERATOR', 'ADMIN')")
    public ResponseEntity<ApiResponse<MissionResponse>> assignDrone(
            @PathVariable String id,
            @RequestParam String droneId) {
        MissionResponse response = missionService.assignDrone(id, droneId);
        return ResponseEntity.ok(ApiResponse.ok("Đã gán Drone thành công", response));
    }

    /**
     * POST /api/missions/{id}/assign-operator
     * Manager assigns Operator to the mission.
     */
    @PostMapping("/{id}/assign-operator")
    @PreAuthorize("hasAnyRole('STAFF', 'SYSTEM_OPERATOR', 'ADMIN')")
    public ResponseEntity<ApiResponse<MissionResponse>> assignOperator(
            @PathVariable String id,
            @RequestParam String operatorId) {
        MissionResponse response = missionService.assignOperator(id, operatorId);
        return ResponseEntity.ok(ApiResponse.ok("Đã gán Operator thành công", response));
    }

    @PreAuthorize("hasAnyRole('STAFF', 'SYSTEM_OPERATOR', 'ADMIN')")
    @PostMapping("/{id}/assign-resources")
    public ResponseEntity<ApiResponse<MissionResponse>> assignResources(
            @PathVariable String id,
            @Valid @RequestBody MissionResourceAssignmentRequest request) {
        MissionResponse response = missionService.assignResources(
                id, request.getDroneId(), request.getOperatorId());
        return ResponseEntity.ok(ApiResponse.ok("Mission resources assigned successfully", response));
    }

    // ------------------------------------------------------------------
    // F3.1 – Operator acceptance
    // ------------------------------------------------------------------

    /**
     * PATCH /api/missions/{id}/accept
     * Drone Operator confirms they accept the mission.
     * Transitions: WAITING_OPERATOR_ACCEPTANCE → ASTAR_ENERGY_AWARE planning → SCHEDULED
     */
    @PatchMapping("/{id}/accept")
    @PreAuthorize("hasRole('DRONE_OPERATOR') and @missionAuthorizationService.isAssignedOperator(#id)")
    public ResponseEntity<ApiResponse<MissionResponse>> accept(
            @PathVariable String id,
            @RequestHeader(value = "X-Operator-Id", required = false) String ignoredOperatorId) {
        MissionResponse response = missionService.acceptCurrentOperatorMission(id);
        return ResponseEntity.ok(ApiResponse.ok("Mission đã được chấp nhận", response));
    }

    @PreAuthorize("hasRole('DRONE_OPERATOR')")
    @PatchMapping("/{id}/accept-current")
    public ResponseEntity<ApiResponse<MissionResponse>> acceptCurrentOperator(@PathVariable String id) {
        return ResponseEntity.ok(ApiResponse.ok(
                "Mission accepted successfully",
                missionService.acceptCurrentOperatorMission(id)));
    }

    @PreAuthorize("hasRole('DRONE_OPERATOR')")
    @PatchMapping("/{id}/reject-current")
    public ResponseEntity<ApiResponse<MissionResponse>> rejectCurrentOperator(
            @PathVariable String id,
            @Valid @RequestBody MissionRejectRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                "Mission rejected successfully",
                missionService.rejectCurrentOperatorMission(id, request.getReason())));
    }

    /**
     * PATCH /api/missions/{id}/reject
     * Drone Operator rejects the mission with a mandatory reason.
     * Transitions: WAITING_OPERATOR_ACCEPTANCE → RESOURCE_ASSIGNING
     */
    @PatchMapping("/{id}/reject")
    @PreAuthorize("hasRole('DRONE_OPERATOR') and @missionAuthorizationService.isAssignedOperator(#id)")
    public ResponseEntity<ApiResponse<MissionResponse>> reject(
            @PathVariable String id,
            @RequestHeader(value = "X-Operator-Id", required = false) String ignoredOperatorId,
            @Valid @RequestBody MissionRejectRequest request) {
        MissionResponse response = missionService.rejectCurrentOperatorMission(id, request.getReason());
        return ResponseEntity.ok(ApiResponse.ok("Mission đã bị từ chối, hệ thống sẽ phân công lại", response));
    }

    /**
     * POST /api/missions/{id}/connect
     * Confirm telemetry link with GCS app (powerOnAndPairWithGCSApp).
     * Transitions: SCHEDULED -> CONNECTED
     */
    @PostMapping("/{id}/connect")
    @PreAuthorize("hasRole('DRONE_OPERATOR') and @missionAuthorizationService.isAssignedOperator(#id)")
    public ResponseEntity<ApiResponse<MissionResponse>> connectGcs(@PathVariable String id) {
        MissionResponse response = missionService.connectGcs(id);
        return ResponseEntity.ok(ApiResponse.ok("Telemetry link with GCS confirmed (CONNECTED)", response));
    }

    @GetMapping("/{id}/telemetry-readiness")
    @PreAuthorize("hasRole('DRONE_OPERATOR') and @missionAuthorizationService.isAssignedOperator(#id)")
    public ResponseEntity<ApiResponse<MissionTelemetryReadinessResponse>> getTelemetryReadiness(
            @PathVariable String id) {
        return ResponseEntity.ok(ApiResponse.ok(missionService.getTelemetryReadiness(id)));
    }

    /**
     * POST /api/missions/{id}/disconnect
     * Normal or explicit disconnection from GCS app.
     */
    @PostMapping("/{id}/disconnect")
    @PreAuthorize("hasRole('DRONE_OPERATOR') and @missionAuthorizationService.isAssignedOperator(#id)")
    public ResponseEntity<ApiResponse<MissionResponse>> disconnectGcs(
            @PathVariable String id,
            @RequestParam(required = false, defaultValue = "NORMAL") String reason) {
        MissionResponse response = missionService.disconnectGcs(id, reason);
        return ResponseEntity.ok(ApiResponse.ok("GCS session disconnected (DISCONNECTED)", response));
    }

    /**
     * POST /api/missions/{id}/gcs-lost
     * Report GCS telemetry signal loss (LOST), triggering automatic Return-To-Launch (RTL).
     */
    @PostMapping("/{id}/gcs-lost")
    @PreAuthorize("hasRole('DRONE_OPERATOR') and @missionAuthorizationService.isAssignedOperator(#id)")
    public ResponseEntity<ApiResponse<MissionResponse>> reportGcsLost(
            @PathVariable String id,
            @RequestParam(required = false, defaultValue = "SIGNAL_LOSS") String reason) {
        MissionResponse response = missionService.handleGcsSessionLost(id, reason);
        return ResponseEntity.ok(ApiResponse.ok("GCS signal LOST – Return-To-Launch (RTL) triggered", response));
    }

    @PostMapping("/{id}/preflight-check")
    @PreAuthorize("hasRole('DRONE_OPERATOR') and @missionAuthorizationService.isAssignedOperator(#id)")
    public ResponseEntity<ApiResponse<PreflightCheckResponse>> runPreflightCheck(
            @PathVariable String id,
            @RequestParam String droneCode) {
        PreflightCheckResponse response = missionService.runPreflightCheck(id, droneCode);
        boolean passed = Boolean.TRUE.equals(response.getOverallPassed());
        return ResponseEntity
                .status(passed ? HttpStatus.OK.value() : 422)
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
    @PreAuthorize("hasRole('DRONE_OPERATOR') and @missionAuthorizationService.isAssignedOperator(#id)")
    public ResponseEntity<ApiResponse<MissionResponse>> replaceDrone(
            @PathVariable String id,
            @Valid @RequestBody DroneReplacementRequest request) {
        MissionResponse response = missionService.replaceDrone(id, request.getNewDroneCode());
        return ResponseEntity.ok(ApiResponse.ok("Drone successfully replaced", response));
    }

    // ------------------------------------------------------------------
    // F3.2b – Bàn giao quyền điều khiển (Handover of control)
    // ------------------------------------------------------------------

    /**
     * POST /api/missions/{id}/handover
     * Operator formally accepts control of the drone console.
     */
    @PostMapping("/{id}/handover")
    @PreAuthorize("hasRole('DRONE_OPERATOR') and @missionAuthorizationService.isAssignedOperator(#id)")
    public ResponseEntity<ApiResponse<MissionResponse>> handover(
            @PathVariable String id,
            @RequestHeader(value = "X-Operator-Id", required = false) String ignoredOperatorId) {
        MissionResponse response = missionService.handoverCurrentOperatorControl(id);
        return ResponseEntity.ok(ApiResponse.ok("Control handed over", response));
    }

    @PreAuthorize("hasRole('DRONE_OPERATOR')")
    @PostMapping("/{id}/handover-current")
    public ResponseEntity<ApiResponse<MissionResponse>> handoverCurrentOperator(@PathVariable String id) {
        return ResponseEntity.ok(ApiResponse.ok(
                "Mission control handed over successfully",
                missionService.handoverCurrentOperatorControl(id)));
    }

    // ------------------------------------------------------------------
    // F3.3 – Mission execution lifecycle
    // ------------------------------------------------------------------

    /**
     * POST /api/missions/{id}/start
     * Operator clicks "Press Takeoff". Validates Flight Access Token.
     */
    @PostMapping("/{id}/start")
    @PreAuthorize("hasRole('DRONE_OPERATOR') and @missionAuthorizationService.isAssignedOperator(#id)")
    public ResponseEntity<ApiResponse<MissionResponse>> start(
            @PathVariable String id,
            @RequestParam(required = false) String tokenValue) {
        MissionResponse response = missionService.startMission(id, tokenValue);
        return ResponseEntity.ok(ApiResponse.ok("Takeoff confirmed – mission IN_FLIGHT", response));
    }

    /** POST /api/missions/{id}/return – drone heads back, RETURNING */
    @PostMapping("/{id}/return")
    @PreAuthorize("hasRole('DRONE_OPERATOR') and @missionAuthorizationService.isAssignedOperator(#id)")
    public ResponseEntity<ApiResponse<MissionResponse>> markReturning(@PathVariable String id) {
        MissionResponse response = missionService.markReturning(id);
        return ResponseEntity.ok(ApiResponse.ok("Drone đang quay trở về", response));
    }

    /** POST /api/missions/{id}/postflight – drone landed, POSTFLIGHT_CHECKING */
    @PostMapping("/{id}/postflight")
    @PreAuthorize("hasRole('DRONE_OPERATOR') and @missionAuthorizationService.isAssignedOperator(#id)")
    public ResponseEntity<ApiResponse<MissionResponse>> startPostflight(@PathVariable String id) {
        MissionResponse response = missionService.startPostflightChecking(id);
        return ResponseEntity.ok(ApiResponse.ok("Bắt đầu kiểm tra sau bay", response));
    }

    /** POST /api/missions/{id}/complete – operator confirms complete */
    @PostMapping("/{id}/complete")
    @PreAuthorize("hasRole('DRONE_OPERATOR') and @missionAuthorizationService.isAssignedOperator(#id)")
    public ResponseEntity<ApiResponse<MissionResponse>> complete(@PathVariable String id) {
        MissionResponse response = missionService.completeMission(id);
        return ResponseEntity.ok(ApiResponse.ok("Mission đã hoàn thành", response));
    }

    /** POST /api/missions/{id}/fail – operator reports mission failure */
    @PostMapping("/{id}/fail")
    @PreAuthorize("hasRole('DRONE_OPERATOR') and @missionAuthorizationService.isAssignedOperator(#id)")
    public ResponseEntity<ApiResponse<MissionResponse>> fail(
            @PathVariable String id,
            @Valid @RequestBody MissionFailRequest request) {
        MissionResponse response = missionService.failMission(id, request.getReason());
        return ResponseEntity.ok(ApiResponse.ok("Mission đã báo lỗi thất bại", response));
    }

    // ------------------------------------------------------------------
    // F3.4 – Media Upload (with retry & notification on failure)
    // ------------------------------------------------------------------

    /**
     * POST /api/missions/{id}/media (Multipart)
     */
    @PostMapping(value = "/{id}/media", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('DRONE_OPERATOR') and @missionAuthorizationService.isAssignedOperator(#id)")
    public ResponseEntity<ApiResponse<MediaAssetResponse>> uploadMedia(
            @PathVariable String id,
            @RequestParam(required = false) String droneCode,
            @RequestParam(required = false) String droneId,
            @RequestParam(required = false) String capturedAt,
            @RequestParam(required = false) String mediaType,
            @RequestParam("file") MultipartFile file) {
        String resolvedDroneCode = droneCode != null && !droneCode.isBlank() ? droneCode : droneId;
        MediaAsset saved = missionMediaUploadService.uploadWithRetry(id, resolvedDroneCode, file, mediaType);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Tải media thành công", mediaAssetMapper.toResponse(saved)));
    }

    // ------------------------------------------------------------------
    // F3.5 – Post-flight status update
    // ------------------------------------------------------------------

    /**
     * PATCH /api/missions/{id}/postflight-status
     */
    @PatchMapping("/{id}/postflight-status")
    @PreAuthorize("hasRole('DRONE_OPERATOR') and @missionAuthorizationService.isAssignedOperator(#id)")
    public ResponseEntity<ApiResponse<MissionResponse>> updatePostFlightStatus(
            @PathVariable String id,
            @RequestParam String droneCode,
            @Valid @RequestBody PostFlightStatusRequest request) {
        MissionResponse response = missionService.recordPostFlightInspection(
                id, request.getNewDroneStatus(), request.getNotes(), request.getInspectionResults());
        return ResponseEntity.ok(ApiResponse.ok("Cập nhật trạng thái drone sau bay thành công", response));
    }
}
