package com.ondemandmonitoring.service.controller;

import com.ondemandmonitoring.common.api.ApiResponse;
import com.ondemandmonitoring.service.dto.request.ServiceRequest;
import com.ondemandmonitoring.service.dto.response.ServicePricingEstimateResponse;
import com.ondemandmonitoring.service.dto.response.ServiceRequirementSuggestionResponse;
import com.ondemandmonitoring.service.dto.response.ServiceResponse;
import com.ondemandmonitoring.service.domain.ServiceRequirementSuggestion;
import com.ondemandmonitoring.service.repository.ServiceRequirementSuggestionRepository;
import com.ondemandmonitoring.service.service.IServiceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.util.List;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;
import org.springframework.beans.factory.annotation.Value;
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

@Tag(name = "Services", description = "APIs for managing monitoring services")
@RestController
@RequestMapping("/api/services")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ServiceController {

    IServiceService serviceService;
    ServiceRequirementSuggestionRepository suggestionRepository;

    @NonFinal
    @Value("${odm.pricing.default-service-price:0}")
    BigDecimal defaultServicePrice;

    @NonFinal
    @Value("${odm.pricing.addons.ai-image-analysis:0}")
    BigDecimal aiImageAnalysisPrice;

    @Operation(summary = "Create service", description = "Creates a new monitoring service")
    @PostMapping
    public ResponseEntity<ApiResponse<ServiceResponse>> create(
            @Valid @RequestBody ServiceRequest request) {
        ServiceResponse response = serviceService.create(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.created("Service created successfully", response));
    }

    @Operation(summary = "Get service by ID", description = "Retrieves a service by its ID")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ServiceResponse>> getById(@PathVariable String id) {
        ServiceResponse response = serviceService.getById(id);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @Operation(summary = "Get all services", description = "Retrieves all services. Use activeOnly=true to filter active services only.")
    @GetMapping
    public ResponseEntity<ApiResponse<List<ServiceResponse>>> getAll(
            @RequestParam(required = false, defaultValue = "false") boolean activeOnly) {
        List<ServiceResponse> response = activeOnly
                ? serviceService.getAllActive()
                : serviceService.getAll();
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @Operation(summary = "Get requirement suggestions", description = "Retrieves DB-driven quick requirement suggestions for a service.")
    @GetMapping("/requirement-suggestions")
    public ResponseEntity<ApiResponse<List<ServiceRequirementSuggestionResponse>>> getRequirementSuggestions(
            @RequestParam(required = false) String serviceId) {
        List<ServiceRequirementSuggestion> rows = new ArrayList<>();
        if (serviceId != null && !serviceId.isBlank()) {
            rows.addAll(suggestionRepository.findByActiveTrueAndServiceIdInOrderBySortOrderAscCreatedAtAsc(List.of(serviceId)));
        }
        rows.addAll(suggestionRepository.findByActiveTrueAndServiceIsNullOrderBySortOrderAscCreatedAtAsc());

        Map<String, ServiceRequirementSuggestionResponse> unique = new LinkedHashMap<>();
        for (ServiceRequirementSuggestion row : rows) {
            String key = row.getCategory().toLowerCase() + "|" + row.getLabel().toLowerCase();
            unique.putIfAbsent(key, toSuggestionResponse(row));
        }

        return ResponseEntity.ok(ApiResponse.ok(new ArrayList<>(unique.values())));
    }

    @Operation(summary = "Estimate service pricing", description = "Returns backend-owned pricing for the selected service and optional add-ons.")
    @GetMapping("/pricing-estimate")
    public ResponseEntity<ApiResponse<ServicePricingEstimateResponse>> estimatePricing(
            @RequestParam String serviceId,
            @RequestParam(defaultValue = "false") boolean aiImageAnalysis) {

        List<ServicePricingEstimateResponse.AdditionalRequirementPrice> additionalRequirements =
                new ArrayList<>();

        if (aiImageAnalysis) {
            additionalRequirements.add(ServicePricingEstimateResponse.AdditionalRequirementPrice.builder()
                    .type("AI_IMAGE_ANALYSIS")
                    .description("Phân tích hình ảnh và đánh dấu dấu hiệu bất thường")
                    .additionalPrice(aiImageAnalysisPrice)
                    .build());
        }

        BigDecimal additionalTotal = additionalRequirements.stream()
                .map(ServicePricingEstimateResponse.AdditionalRequirementPrice::additionalPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        ServicePricingEstimateResponse response = ServicePricingEstimateResponse.builder()
                .serviceId(serviceId)
                .servicePrice(defaultServicePrice)
                .additionalRequirements(additionalRequirements)
                .totalPrice(defaultServicePrice.add(additionalTotal))
                .build();

        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @Operation(summary = "Update service", description = "Updates an existing service by its ID")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<ServiceResponse>> update(
            @PathVariable String id,
            @Valid @RequestBody ServiceRequest request) {
        ServiceResponse response = serviceService.update(id, request);
        return ResponseEntity.ok(ApiResponse.ok("Service updated successfully", response));
    }

    @Operation(summary = "Delete service", description = "Soft-deletes a service by setting isActive to false")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable String id) {
        serviceService.delete(id);
        return ResponseEntity.ok(ApiResponse.ok("Service deleted successfully", null));
    }

    private ServiceRequirementSuggestionResponse toSuggestionResponse(ServiceRequirementSuggestion row) {
        return ServiceRequirementSuggestionResponse.builder()
                .id(row.getId())
                .serviceId(row.getService() != null ? row.getService().getId() : null)
                .category(row.getCategory())
                .label(row.getLabel())
                .message(row.getMessage())
                .sortOrder(row.getSortOrder())
                .source(row.getSource())
                .build();
    }
}
