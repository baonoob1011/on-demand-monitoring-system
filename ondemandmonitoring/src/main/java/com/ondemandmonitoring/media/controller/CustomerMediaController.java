package com.ondemandmonitoring.media.controller;

import com.ondemandmonitoring.common.api.ApiResponse;
import com.ondemandmonitoring.media.dto.response.CustomerMediaNotificationResponse;
import com.ondemandmonitoring.media.dto.response.CustomerMediaResponse;
import com.ondemandmonitoring.media.service.ICustomerMediaService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@PreAuthorize("hasRole('CUSTOMER')")
public class CustomerMediaController {
    private final ICustomerMediaService customerMedia;

    @GetMapping("/customer/available-media")
    public ApiResponse<List<CustomerMediaResponse>> allAvailable() {
        return ApiResponse.ok(customerMedia.listAllAvailable());
    }

    @GetMapping("/customer/media-notifications")
    public ApiResponse<List<CustomerMediaNotificationResponse>> allNotifications() {
        return ApiResponse.ok(customerMedia.listAllNotifications());
    }

    @GetMapping("/missions/{missionId}/available-media")
    public ApiResponse<List<CustomerMediaResponse>> list(@PathVariable String missionId) {
        return ApiResponse.ok(customerMedia.listAvailable(missionId));
    }

    @GetMapping("/media/{mediaId}/download")
    public ApiResponse<CustomerMediaResponse> download(@PathVariable String mediaId) {
        return ApiResponse.ok(customerMedia.getAvailable(mediaId));
    }

    @GetMapping("/missions/{missionId}/media-notifications")
    public ApiResponse<List<CustomerMediaNotificationResponse>> notifications(@PathVariable String missionId) {
        return ApiResponse.ok(customerMedia.listNotifications(missionId));
    }
}
