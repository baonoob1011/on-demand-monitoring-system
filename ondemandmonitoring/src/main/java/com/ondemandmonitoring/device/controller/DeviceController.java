package com.ondemandmonitoring.device.controller;

import com.ondemandmonitoring.common.api.ApiResponse;
import com.ondemandmonitoring.device.domain.Device;
import com.ondemandmonitoring.device.service.IDeviceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Device Management", description = "APIs for managing drones")
@RestController
@RequestMapping("/api/devices")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class DeviceController {

    IDeviceService deviceService;

    @Operation(summary = "Get available drones", description = "Get a list of available drones for a specific order")
    @GetMapping("/available")
    public ResponseEntity<ApiResponse<List<Device>>> getAvailableDrones(@RequestParam String orderId) {
        List<Device> availableDrones = deviceService.getAvailableDrones(orderId);
        return ResponseEntity.ok(ApiResponse.ok("Available drones fetched successfully", availableDrones));
    }
}
