package com.ondemandmonitoring.service.controller;

import com.ondemandmonitoring.common.api.ApiResponse;
import com.ondemandmonitoring.service.dto.request.DeliverableTypeRequest;
import com.ondemandmonitoring.service.dto.response.DeliverableTypeResponse;
import com.ondemandmonitoring.service.service.IDeliverableTypeService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Deliverable Types", description = "APIs for managing deliverable types")
@RestController
@RequestMapping("/api/deliverable-types")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class DeliverableTypeController {

    IDeliverableTypeService deliverableTypeService;

    @Operation(summary = "Create deliverable type", description = "Creates a new deliverable type")
    @PostMapping
    public ResponseEntity<ApiResponse<DeliverableTypeResponse>> create(
            @Valid @RequestBody DeliverableTypeRequest request) {
        DeliverableTypeResponse response = deliverableTypeService.create(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.created("Deliverable type created successfully", response));
    }

    @Operation(summary = "Get deliverable type by ID", description = "Retrieves a deliverable type by its ID")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<DeliverableTypeResponse>> getById(@PathVariable String id) {
        DeliverableTypeResponse response = deliverableTypeService.getById(id);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @Operation(summary = "Get all deliverable types", description = "Retrieves all deliverable types. Use activeOnly=true to filter active types only.")
    @GetMapping
    public ResponseEntity<ApiResponse<List<DeliverableTypeResponse>>> getAll(
            @RequestParam(required = false, defaultValue = "false") boolean activeOnly) {
        List<DeliverableTypeResponse> response = activeOnly
                ? deliverableTypeService.getAllActive()
                : deliverableTypeService.getAll();
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @Operation(summary = "Update deliverable type", description = "Updates an existing deliverable type by its ID")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<DeliverableTypeResponse>> update(
            @PathVariable String id,
            @Valid @RequestBody DeliverableTypeRequest request) {
        DeliverableTypeResponse response = deliverableTypeService.update(id, request);
        return ResponseEntity.ok(ApiResponse.ok("Deliverable type updated successfully", response));
    }

    @Operation(summary = "Delete deliverable type", description = "Soft-deletes a deliverable type by setting isActive to false")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable String id) {
        deliverableTypeService.delete(id);
        return ResponseEntity.ok(ApiResponse.ok("Deliverable type deleted successfully", null));
    }
}
