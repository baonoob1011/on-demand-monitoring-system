package com.ondemandmonitoring.service.controller;

import com.ondemandmonitoring.common.api.ApiResponse;
import com.ondemandmonitoring.service.dto.request.ServiceDeliverableRequest;
import com.ondemandmonitoring.service.dto.response.ServiceDeliverableResponse;
import com.ondemandmonitoring.service.service.IServiceDeliverableService;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Service Deliverables", description = "APIs for managing service-deliverable type relationships")
@RestController
@RequestMapping("/api/service-deliverables")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ServiceDeliverableController {

    IServiceDeliverableService serviceDeliverableService;

    @Operation(summary = "Create service-deliverable link",
            description = "Links a monitoring service with a deliverable type")
    @PostMapping
    public ResponseEntity<ApiResponse<ServiceDeliverableResponse>> create(
            @Valid @RequestBody ServiceDeliverableRequest request) {
        ServiceDeliverableResponse response = serviceDeliverableService.create(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.created("Service deliverable link created successfully", response));
    }

    @Operation(summary = "Get all service-deliverable links",
            description = "Retrieves all links. Filter by serviceId or deliverableTypeId query param.")
    @GetMapping
    public ResponseEntity<ApiResponse<List<ServiceDeliverableResponse>>> getAll(
            @RequestParam(required = false) String serviceId,
            @RequestParam(required = false) String deliverableTypeId) {
        List<ServiceDeliverableResponse> response;
        if (serviceId != null) {
            response = serviceDeliverableService.getByServiceId(serviceId);
        } else if (deliverableTypeId != null) {
            response = serviceDeliverableService.getByDeliverableTypeId(deliverableTypeId);
        } else {
            response = serviceDeliverableService.getAll();
        }
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @Operation(summary = "Delete service-deliverable link",
            description = "Removes a service-deliverable link by its ID")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable String id) {
        serviceDeliverableService.delete(id);
        return ResponseEntity.ok(ApiResponse.ok("Service deliverable link deleted successfully", null));
    }
}
