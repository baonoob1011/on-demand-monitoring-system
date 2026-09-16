package com.ondemandmonitoring.drone.controller;

import com.ondemandmonitoring.common.api.ApiResponse;
import com.ondemandmonitoring.common.api.PageResponse;
import com.ondemandmonitoring.drone.dto.request.DroneModelCreateRequest;
import com.ondemandmonitoring.drone.dto.request.DroneModelUpdateRequest;
import com.ondemandmonitoring.drone.dto.response.DroneModelResponse;
import com.ondemandmonitoring.drone.service.IDroneModelService;
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

@Tag(name = "Drone Models", description = "APIs for managing drone models")
@RestController
@RequestMapping("/api/drone-models")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class DroneModelController {

    IDroneModelService droneModelService;

    @Operation(summary = "Create drone model", description = "Creates a new drone model specification")
    @PostMapping
    public ResponseEntity<ApiResponse<DroneModelResponse>> create(@Valid @RequestBody DroneModelCreateRequest request) {
        DroneModelResponse response = droneModelService.create(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.created("Drone model created successfully", response));
    }

    @Operation(summary = "Get drone model by ID", description = "Retrieves details of a drone model by its ID")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<DroneModelResponse>> getById(@PathVariable String id) {
        DroneModelResponse response = droneModelService.getById(id);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @Operation(summary = "Get all drone models", description = "Retrieves a paginated list of drone models with optional category/manufacturer filter")
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<DroneModelResponse>>> getAll(
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String manufacturer) {
        PageResponse<DroneModelResponse> response = droneModelService.getAll(pageable, category, manufacturer);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @Operation(summary = "Update drone model", description = "Updates an existing drone model by its ID")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<DroneModelResponse>> update(
            @PathVariable String id,
            @Valid @RequestBody DroneModelUpdateRequest request) {
        DroneModelResponse response = droneModelService.update(id, request);
        return ResponseEntity.ok(ApiResponse.ok("Drone model updated successfully", response));
    }

    @Operation(summary = "Delete drone model", description = "Deletes a drone model by its ID")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable String id) {
        droneModelService.delete(id);
        return ResponseEntity.ok(ApiResponse.ok("Drone model deleted successfully", null));
    }
}
