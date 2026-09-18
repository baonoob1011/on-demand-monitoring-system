package com.ondemandmonitoring.drone.controller;

import com.ondemandmonitoring.common.api.ApiResponse;
import com.ondemandmonitoring.common.api.PageResponse;
import com.ondemandmonitoring.drone.dto.request.DroneCreateRequest;
import com.ondemandmonitoring.drone.dto.request.DroneUpdateRequest;
import com.ondemandmonitoring.drone.dto.response.DroneResponse;
import com.ondemandmonitoring.drone.enums.DroneStatus;
import com.ondemandmonitoring.drone.service.IDroneService;
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

@Tag(name = "Drones", description = "APIs for managing physical drones")
@RestController
@RequestMapping("/api/drones")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class DroneController {

    IDroneService droneService;

    @Operation(summary = "Create drone", description = "Creates a new drone with reference to DroneModel and optional DronePayload")
    @PostMapping
    public ResponseEntity<ApiResponse<DroneResponse>> create(@Valid @RequestBody DroneCreateRequest request) {
        DroneResponse response = droneService.create(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.created("Drone created successfully", response));
    }

    @Operation(summary = "Get drone by ID", description = "Retrieves details of a drone by its ID")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<DroneResponse>> getById(@PathVariable String id) {
        DroneResponse response = droneService.getById(id);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @Operation(summary = "Get all drones", description = "Retrieves a paginated list of drones with optional filtering by modelId, payloadId, or status")
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<DroneResponse>>> getAll(
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            @RequestParam(required = false) String modelId,
            @RequestParam(required = false) String payloadId,
            @RequestParam(required = false) DroneStatus status) {
        PageResponse<DroneResponse> response = droneService.getAll(pageable, modelId, payloadId, status);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @Operation(summary = "Update drone", description = "Updates an existing drone by its ID")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<DroneResponse>> update(
            @PathVariable String id,
            @Valid @RequestBody DroneUpdateRequest request) {
        DroneResponse response = droneService.update(id, request);
        return ResponseEntity.ok(ApiResponse.ok("Drone updated successfully", response));
    }

    @Operation(summary = "Delete drone", description = "Deletes a drone by its ID")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable String id) {
        droneService.delete(id);
        return ResponseEntity.ok(ApiResponse.ok("Drone deleted successfully", null));
    }
}
