package com.ondemandmonitoring.categoryservice.controller;

import com.ondemandmonitoring.categoryservice.dto.request.CategoryServiceRequest;
import com.ondemandmonitoring.categoryservice.dto.response.CategoryServiceResponse;
import com.ondemandmonitoring.categoryservice.service.CategoryServiceService;
import com.ondemandmonitoring.common.api.ApiResponse;
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

@Tag(name = "Category Services", description = "APIs for managing service categories")
@RestController
@RequestMapping("/api/category-services")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class CategoryServiceController {

    CategoryServiceService categoryServiceService;

    @Operation(summary = "Create category service", description = "Creates a new category service")
    @PostMapping
    public ResponseEntity<ApiResponse<CategoryServiceResponse>> create(@Valid @RequestBody CategoryServiceRequest request) {
        CategoryServiceResponse response = categoryServiceService.create(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.created("Category service created successfully", response));
    }

    @Operation(summary = "Get category service by ID", description = "Retrieves details of a category service by its ID")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<CategoryServiceResponse>> getById(@PathVariable Long id) {
        CategoryServiceResponse response = categoryServiceService.getById(id);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @Operation(summary = "Get all category services", description = "Retrieves a list of all category services")
    @GetMapping
    public ResponseEntity<ApiResponse<List<CategoryServiceResponse>>> getAll() {
        List<CategoryServiceResponse> response = categoryServiceService.getAll();
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @Operation(summary = "Update category service", description = "Updates an existing category service by its ID")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<CategoryServiceResponse>> update(
            @PathVariable Long id,
            @Valid @RequestBody CategoryServiceRequest request) {
        CategoryServiceResponse response = categoryServiceService.update(id, request);
        return ResponseEntity.ok(ApiResponse.ok("Category service updated successfully", response));
    }

    @Operation(summary = "Delete category service", description = "Deletes a category service by its ID")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        categoryServiceService.delete(id);
        return ResponseEntity.ok(ApiResponse.ok("Category service deleted successfully", null));
    }
}
