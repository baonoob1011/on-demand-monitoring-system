package com.ondemandmonitoring.checklist.controller;

import com.ondemandmonitoring.checklist.dto.request.*;
import com.ondemandmonitoring.checklist.dto.response.ServiceChecklistResponse;
import com.ondemandmonitoring.checklist.service.IServiceChecklistService;
import com.ondemandmonitoring.common.api.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/services/{serviceId}/checklists")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class ServiceChecklistController {
    private final IServiceChecklistService service;

    @PostMapping
    public ResponseEntity<ApiResponse<ServiceChecklistResponse>> assign(
            @PathVariable String serviceId, @Valid @RequestBody ChecklistAssignmentRequest request) {
        return ResponseEntity.status(201).body(ApiResponse.created("Checklist assigned", service.assign(serviceId, request)));
    }

    @PatchMapping("/{checklistId}")
    public ApiResponse<ServiceChecklistResponse> updateOrder(@PathVariable String serviceId,
            @PathVariable String checklistId, @Valid @RequestBody ChecklistOrderRequest request) {
        return ApiResponse.ok(service.updateOrder(serviceId, checklistId, request.getDisplayOrder()));
    }

    @DeleteMapping("/{checklistId}")
    public ApiResponse<Void> unassign(@PathVariable String serviceId, @PathVariable String checklistId) {
        service.unassign(serviceId, checklistId);
        return ApiResponse.ok("Checklist unassigned", null);
    }
}
