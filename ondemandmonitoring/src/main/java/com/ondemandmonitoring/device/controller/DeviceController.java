package com.ondemandmonitoring.device.controller;

import com.ondemandmonitoring.common.api.ApiResponse;
import com.ondemandmonitoring.common.api.PageResponse;
import com.ondemandmonitoring.device.dto.request.DeviceCreateRequest;
import com.ondemandmonitoring.device.dto.request.DeviceUpdateRequest;
import com.ondemandmonitoring.device.dto.response.DeviceResponse;
import com.ondemandmonitoring.device.service.IDeviceService;
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

@Tag(name = "Devices", description = "APIs for managing bound devices")
@RestController
@RequestMapping("/api/devices")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class DeviceController {

    IDeviceService deviceService;

    @Operation(summary = "Create device", description = "Creates a new physical device binding with serial number and model ID")
    @PostMapping
    public ResponseEntity<ApiResponse<DeviceResponse>> create(@Valid @RequestBody DeviceCreateRequest request) {
        DeviceResponse response = deviceService.create(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.created("Device created successfully", response));
    }

    @Operation(summary = "Get device by ID", description = "Retrieves details of a device by its ID")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<DeviceResponse>> getById(@PathVariable String id) {
        DeviceResponse response = deviceService.getById(id);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @Operation(summary = "Get all devices", description = "Retrieves a paginated list of devices")
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<DeviceResponse>>> getAll(
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        PageResponse<DeviceResponse> response = deviceService.getAll(pageable);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @Operation(summary = "Update device", description = "Updates an existing device by its ID")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<DeviceResponse>> update(
            @PathVariable String id,
            @Valid @RequestBody DeviceUpdateRequest request) {
        DeviceResponse response = deviceService.update(id, request);
        return ResponseEntity.ok(ApiResponse.ok("Device updated successfully", response));
    }

    @Operation(summary = "Delete device", description = "Deletes a device by its ID")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable String id) {
        deviceService.delete(id);
        return ResponseEntity.ok(ApiResponse.ok("Device deleted successfully", null));
    }
}
