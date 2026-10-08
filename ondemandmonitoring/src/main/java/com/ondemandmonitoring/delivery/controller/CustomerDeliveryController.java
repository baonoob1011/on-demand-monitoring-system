package com.ondemandmonitoring.delivery.controller;

import com.ondemandmonitoring.common.api.ApiResponse;
import com.ondemandmonitoring.delivery.dto.request.RevisionRequest;
import com.ondemandmonitoring.delivery.dto.response.DeliveryResponse;
import com.ondemandmonitoring.delivery.service.IDeliveryWorkflowService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** Customer-facing endpoints for reviewing and accepting delivered results. */
@RestController
@RequestMapping("/api/orders/{orderId}")
@PreAuthorize("hasRole('CUSTOMER')")
@RequiredArgsConstructor
public class CustomerDeliveryController {
    private final IDeliveryWorkflowService delivery;

    @GetMapping("/delivery")
    public ApiResponse<DeliveryResponse> get(@PathVariable String orderId) {
        return ApiResponse.ok(delivery.get(orderId));
    }

    @GetMapping("/deliverables/preview")
    public ApiResponse<DeliveryResponse> previews(@PathVariable String orderId) {
        return ApiResponse.ok(delivery.previews(orderId));
    }

    @GetMapping("/deliverables")
    public ApiResponse<DeliveryResponse> originals(@PathVariable String orderId) {
        return ApiResponse.ok(delivery.originals(orderId));
    }

    @PostMapping("/result/accept")
    public ApiResponse<DeliveryResponse> accept(@PathVariable String orderId) {
        return ApiResponse.ok("Result accepted", delivery.acceptResult(orderId));
    }

    @PostMapping("/result/revision-request")
    public ApiResponse<DeliveryResponse> revision(
            @PathVariable String orderId,
            @Valid @RequestBody RevisionRequest request) {
        return ApiResponse.ok("Revision requested", delivery.requestRevision(orderId, request));
    }
}
