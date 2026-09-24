package com.ondemandmonitoring.media.controller;

import com.ondemandmonitoring.common.api.ApiResponse;
import com.ondemandmonitoring.media.dto.response.CustomerMediaNotificationResponse;
import com.ondemandmonitoring.media.dto.response.CustomerMediaResponse;
import com.ondemandmonitoring.media.service.ICustomerMediaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Customer Media", description = "APIs for customer media retrieval and notifications")
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@PreAuthorize("hasRole('CUSTOMER')")
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class CustomerMediaController {

    ICustomerMediaService customerMedia;

    @Operation(summary = "List all available media for customer", description = "Retrieves all ready media assets belonging to customer's orders")
    @GetMapping("/customer/available-media")
    public ResponseEntity<ApiResponse<List<CustomerMediaResponse>>> allAvailable() {
        return ResponseEntity.ok(ApiResponse.ok(customerMedia.listAllAvailable()));
    }

    @Operation(summary = "List all media notifications for customer", description = "Retrieves media notification outbox items for customer's orders")
    @GetMapping("/customer/media-notifications")
    public ResponseEntity<ApiResponse<List<CustomerMediaNotificationResponse>>> allNotifications() {
        return ResponseEntity.ok(ApiResponse.ok(customerMedia.listAllNotifications()));
    }

    @Operation(summary = "List available media by mission", description = "Retrieves available media assets for a specific mission")
    @GetMapping("/missions/{missionId}/available-media")
    public ResponseEntity<ApiResponse<List<CustomerMediaResponse>>> list(@PathVariable String missionId) {
        return ResponseEntity.ok(ApiResponse.ok(customerMedia.listAvailable(missionId)));
    }

    @Operation(summary = "Download media asset", description = "Retrieves presigned download URL for a specific available media asset")
    @GetMapping("/media/{mediaId}/download")
    public ResponseEntity<ApiResponse<CustomerMediaResponse>> download(@PathVariable String mediaId) {
        return ResponseEntity.ok(ApiResponse.ok(customerMedia.getAvailable(mediaId)));
    }

    @Operation(summary = "List media notifications by mission", description = "Retrieves media notifications for a specific mission")
    @GetMapping("/missions/{missionId}/media-notifications")
    public ResponseEntity<ApiResponse<List<CustomerMediaNotificationResponse>>> notifications(
            @PathVariable String missionId) {
        return ResponseEntity.ok(ApiResponse.ok(customerMedia.listNotifications(missionId)));
    }
}
