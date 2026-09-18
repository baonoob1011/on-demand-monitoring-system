package com.ondemandmonitoring.warehouse.controller;

import com.ondemandmonitoring.common.api.ApiResponse;
import com.ondemandmonitoring.warehouse.dto.request.PreferredTimeCreateRequest;
import com.ondemandmonitoring.warehouse.dto.request.PreferredTimeUpdateRequest;
import com.ondemandmonitoring.warehouse.dto.response.PreferredTimeResponse;
import com.ondemandmonitoring.warehouse.enums.PreferredTimeCode;
import com.ondemandmonitoring.warehouse.service.IPreferredTimeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Preferred Times", description = "APIs for managing preferred time slots in warehouse")
@RestController
@RequestMapping("/api/preferred-times")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class PreferredTimeController {

    IPreferredTimeService preferredTimeService;

    @Operation(summary = "Create preferred time", description = "Creates a new preferred time slot")
    @PostMapping
    public ResponseEntity<ApiResponse<PreferredTimeResponse>> create(@Valid @RequestBody PreferredTimeCreateRequest request) {
        PreferredTimeResponse response = preferredTimeService.create(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.created("Preferred time created successfully", response));
    }

    @Operation(summary = "Get preferred time by ID", description = "Retrieves a preferred time slot by its ID")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<PreferredTimeResponse>> getById(@PathVariable String id) {
        PreferredTimeResponse response = preferredTimeService.getById(id);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @Operation(summary = "Get preferred time by code", description = "Retrieves a preferred time slot by its code")
    @GetMapping("/code/{code}")
    public ResponseEntity<ApiResponse<PreferredTimeResponse>> getByCode(@PathVariable PreferredTimeCode code) {
        PreferredTimeResponse response = preferredTimeService.getByCode(code);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @Operation(summary = "Get all preferred times", description = "Retrieves all preferred time slots")
    @GetMapping
    public ResponseEntity<ApiResponse<List<PreferredTimeResponse>>> getAll() {
        List<PreferredTimeResponse> response = preferredTimeService.getAll();
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @Operation(summary = "Update preferred time", description = "Updates an existing preferred time slot by ID")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<PreferredTimeResponse>> update(
            @PathVariable String id,
            @Valid @RequestBody PreferredTimeUpdateRequest request) {
        PreferredTimeResponse response = preferredTimeService.update(id, request);
        return ResponseEntity.ok(ApiResponse.ok("Preferred time updated successfully", response));
    }

    @Operation(summary = "Delete preferred time", description = "Deletes a preferred time slot by ID")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable String id) {
        preferredTimeService.delete(id);
        return ResponseEntity.ok(ApiResponse.ok("Preferred time deleted successfully", null));
    }
}
