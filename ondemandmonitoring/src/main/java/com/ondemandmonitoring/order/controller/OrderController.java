package com.ondemandmonitoring.order.controller;

import com.ondemandmonitoring.common.api.ApiResponse;
import com.ondemandmonitoring.order.dto.request.OrderCreateRequest;
import com.ondemandmonitoring.order.dto.response.OrderCreateResponse;
import com.ondemandmonitoring.order.enums.OrderStatus;
import com.ondemandmonitoring.order.service.IOrderService;
import com.ondemandmonitoring.mission.dto.response.MissionResponse;
import com.ondemandmonitoring.common.exception.ApiException;
import com.ondemandmonitoring.common.exception.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;

@Tag(name = "Order Management", description = "APIs for creating and managing monitoring orders")
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class OrderController {

    IOrderService orderService;

    @Operation(summary = "Create a new order", description = "Creates a new monitoring order after validating customer login, service existence, preferred time existence, media attributes, and location point inside zone polygon")
    @PreAuthorize("hasRole('CUSTOMER')")
    @PostMapping
    public ResponseEntity<ApiResponse<OrderCreateResponse>> createOrder(
            @Valid @RequestBody OrderCreateRequest request) {
        OrderCreateResponse response = orderService.createOrder(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.created("Order created successfully", response));
    }

    @Operation(summary = "Get my orders", description = "Customer views their own monitoring orders")
    @PreAuthorize("hasRole('CUSTOMER')")
    @GetMapping("/mine")
    public ResponseEntity<ApiResponse<List<OrderCreateResponse>>> getMyOrders(
            @RequestParam(required = false) OrderStatus status) {
        return ResponseEntity.ok(ApiResponse.ok("Customer orders retrieved successfully", orderService.getMyOrders(status)));
    }

    @Operation(summary = "Get order details", description = "Retrieves details of a specific order by ID")
    @GetMapping("/{orderId}")
    public ResponseEntity<ApiResponse<OrderCreateResponse>> getOrderById(@PathVariable String orderId) {
        OrderCreateResponse response = orderService.getOrderById(orderId);
        return ResponseEntity.ok(ApiResponse.ok("Order details retrieved successfully", response));
    }

    @Operation(summary = "Get order resource preview", description = "Retrieves resource preview for an order (returns null if none)")
    @GetMapping("/{orderId}/resource-preview")
    public ResponseEntity<ApiResponse<Object>> getResourcePreview(@PathVariable String orderId) {
        return ResponseEntity.ok(ApiResponse.ok("Resource preview retrieved", null));
    }

    @Operation(summary = "Get order latest analysis", description = "Retrieves latest AI analysis for an order (returns null if none)")
    @GetMapping("/{orderId}/analysis/latest")
    public ResponseEntity<ApiResponse<Object>> getLatestAnalysis(@PathVariable String orderId) {
        return ResponseEntity.ok(ApiResponse.ok("Latest analysis retrieved", null));
    }

    @Operation(summary = "Approve an order", description = "Manager approves an order and creates a mission")
    @PostMapping("/{orderId}/approve")
    public ResponseEntity<ApiResponse<MissionResponse>> approveOrder(@PathVariable String orderId) {
        MissionResponse mission = orderService.approveOrder(orderId);
        return ResponseEntity.ok(ApiResponse.ok("Order approved and mission created successfully", mission));
    }

    @Operation(summary = "Submit approval decision", description = "Manager submits rejection or need-info decision with reason")
    @PostMapping("/{orderId}/approval")
    public ResponseEntity<ApiResponse<Void>> submitApproval(
            @PathVariable String orderId,
            @RequestBody java.util.Map<String, String> body) {
        String decision = body != null ? body.get("decision") : null;
        String reason = body != null ? body.get("reason") : null;
        if (!"REJECTED".equalsIgnoreCase(decision)) {
            throw new ApiException(
                    ErrorCode.INVALID_REQUEST,
                    "Only REJECTED is currently supported");
        }
        if (reason == null || reason.isBlank()) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "Rejection reason is required");
        }
        orderService.rejectOrder(orderId, reason);
        return ResponseEntity.ok(ApiResponse.ok("Approval decision recorded successfully", null));
    }

    @Operation(summary = "Get pending orders", description = "Manager views pending orders")
    @GetMapping("/pending")
    public ResponseEntity<ApiResponse<java.util.List<OrderCreateResponse>>> getPendingOrders() {
        return ResponseEntity.ok(ApiResponse.ok("Pending orders retrieved", orderService.getPendingOrders()));
    }
}
