package com.ondemandmonitoring.drone.controller;

import com.ondemandmonitoring.common.api.ApiResponse;
import com.ondemandmonitoring.common.api.PageResponse;
import com.ondemandmonitoring.drone.dto.request.DronePayloadCreateRequest;
import com.ondemandmonitoring.drone.dto.request.DronePayloadUpdateRequest;
import com.ondemandmonitoring.drone.dto.response.DronePayloadResponse;
import com.ondemandmonitoring.drone.service.IDronePayloadService;
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

@Tag(name = "Drone Payloads", description = "APIs for managing drone payload specifications")
@RestController
@RequestMapping("/api/drone-payloads")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class DronePayloadController {

    IDronePayloadService dronePayloadService;

    @Operation(summary = "Create drone payload", description = "Creates a new drone payload specification")
    @PostMapping
    public ResponseEntity<ApiResponse<DronePayloadResponse>> create(@Valid @RequestBody DronePayloadCreateRequest request) {
        DronePayloadResponse response = dronePayloadService.create(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.created("Drone payload created successfully", response));
    }

    @Operation(summary = "Get drone payload by ID", description = "Retrieves details of a drone payload by its ID")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<DronePayloadResponse>> getById(@PathVariable String id) {
        DronePayloadResponse response = dronePayloadService.getById(id);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @Operation(summary = "Get all drone payloads", description = "Retrieves a paginated list of drone payloads with optional sensorType/modelName filter")
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<DronePayloadResponse>>> getAll(
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            @RequestParam(required = false) String sensorType,
            @RequestParam(required = false) String modelName) {
        PageResponse<DronePayloadResponse> response = dronePayloadService.getAll(pageable, sensorType, modelName);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @Operation(summary = "Update drone payload", description = "Updates an existing drone payload by its ID")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<DronePayloadResponse>> update(
            @PathVariable String id,
            @Valid @RequestBody DronePayloadUpdateRequest request) {
        DronePayloadResponse response = dronePayloadService.update(id, request);
        return ResponseEntity.ok(ApiResponse.ok("Drone payload updated successfully", response));
    }

    @Operation(summary = "Delete drone payload", description = "Deletes a drone payload by its ID")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable String id) {
        dronePayloadService.delete(id);
        return ResponseEntity.ok(ApiResponse.ok("Drone payload deleted successfully", null));
    }
}
