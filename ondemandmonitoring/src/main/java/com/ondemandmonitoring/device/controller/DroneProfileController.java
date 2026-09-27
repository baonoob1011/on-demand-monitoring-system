package com.ondemandmonitoring.device.controller;

import com.ondemandmonitoring.common.api.ApiResponse;
import com.ondemandmonitoring.common.api.PageResponse;
import com.ondemandmonitoring.device.dto.request.DroneCreateRequest;
import com.ondemandmonitoring.device.dto.request.DroneUpdateRequest;
import com.ondemandmonitoring.device.dto.response.DroneResponse;
import com.ondemandmonitoring.device.enums.DeviceOperationalStatus;
import com.ondemandmonitoring.device.service.IDroneProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Drone Profiles", description = "APIs for managing drone-specific profiles attached to generic devices")
@RestController
@RequestMapping("/api/drones")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class DroneProfileController {

    IDroneProfileService droneProfileService;

    @Operation(summary = "Create drone profile", description = "Creates a drone-specific profile linked to a generic device")
    @PostMapping
    public ResponseEntity<ApiResponse<DroneResponse>> create(@Valid @RequestBody DroneCreateRequest request) {
        DroneResponse response = droneProfileService.create(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.created("Drone profile created successfully", response));
    }

    @Operation(summary = "Get drone profile by ID", description = "Retrieves drone-specific profile details by profile ID")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<DroneResponse>> getById(@PathVariable String id) {
        DroneResponse response = droneProfileService.getById(id);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @Operation(summary = "Get all drone profiles", description = "Retrieves a paginated list of drone profiles with optional filtering by modelId, payloadId, or status")
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<DroneResponse>>> getAll(
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            @RequestParam(required = false) String modelId,
            @RequestParam(required = false) String payloadId,
            @RequestParam(required = false) DeviceOperationalStatus status) {
        PageResponse<DroneResponse> response = droneProfileService.getAll(pageable, modelId, payloadId, status);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @Operation(summary = "Update drone profile", description = "Updates an existing drone-specific profile by profile ID")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<DroneResponse>> update(
            @PathVariable String id,
            @Valid @RequestBody DroneUpdateRequest request) {
        DroneResponse response = droneProfileService.update(id, request);
        return ResponseEntity.ok(ApiResponse.ok("Drone profile updated successfully", response));
    }

    @Operation(summary = "Delete drone profile", description = "Deletes a drone-specific profile by profile ID")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable String id) {
        droneProfileService.delete(id);
        return ResponseEntity.ok(ApiResponse.ok("Drone profile deleted successfully", null));
    }
}
