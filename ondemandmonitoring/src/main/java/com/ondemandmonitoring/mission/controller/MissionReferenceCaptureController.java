package com.ondemandmonitoring.mission.controller;

import com.ondemandmonitoring.common.api.ApiResponse;
import com.ondemandmonitoring.mission.dto.response.ReferenceCaptureResponse;
import com.ondemandmonitoring.mission.service.IMissionReferenceCaptureService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Mission Reference Capture")
@RestController
@RequestMapping("/api/missions/{missionId}/media")
@PreAuthorize("hasAnyRole('DRONE_OPERATOR', 'ADMIN', 'SYSTEM_OPERATOR')")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class MissionReferenceCaptureController {

    IMissionReferenceCaptureService captureService;

    @Operation(summary = "Capture Mapillary reference image",
            description = "Uses the drone's latest telemetry on the server; the client sends no coordinates")
    @PostMapping("/capture-reference")
    public ResponseEntity<ApiResponse<ReferenceCaptureResponse>> captureReference(
            @PathVariable String missionId) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created("Đã lưu ảnh tham chiếu.", captureService.captureMapillaryReference(missionId)));
    }
}
