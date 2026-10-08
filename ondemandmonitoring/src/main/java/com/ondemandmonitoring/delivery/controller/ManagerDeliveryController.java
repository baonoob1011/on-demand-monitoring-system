package com.ondemandmonitoring.delivery.controller;

import com.ondemandmonitoring.common.api.ApiResponse;
import com.ondemandmonitoring.delivery.dto.request.ReleasePreviewRequest;
import com.ondemandmonitoring.delivery.dto.response.DeliveryResponse;
import com.ondemandmonitoring.delivery.service.IDeliveryWorkflowService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** Manager endpoints for preview publication and original-media release. */
@RestController
@RequestMapping("/api/manager/orders/{orderId}/delivery")
@PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
@RequiredArgsConstructor
public class ManagerDeliveryController {
    private final IDeliveryWorkflowService delivery;

    @GetMapping
    public ApiResponse<DeliveryResponse> get(@PathVariable String orderId) {
        return ApiResponse.ok(delivery.get(orderId));
    }

    @PostMapping("/release-preview")
    public ApiResponse<DeliveryResponse> releasePreview(
            @PathVariable String orderId,
            @Valid @RequestBody ReleasePreviewRequest request) {
        return ApiResponse.ok("Preview released", delivery.releasePreview(orderId, request));
    }

    @PostMapping("/release-originals")
    public ApiResponse<DeliveryResponse> releaseOriginals(@PathVariable String orderId) {
        return ApiResponse.ok("Original deliverables released", delivery.releaseOriginals(orderId));
    }
}
