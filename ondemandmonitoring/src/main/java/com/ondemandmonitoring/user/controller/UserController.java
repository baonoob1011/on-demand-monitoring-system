package com.ondemandmonitoring.user.controller;

import com.ondemandmonitoring.common.api.ApiResponse;
import com.ondemandmonitoring.user.dto.request.UserProfileUpdateRequest;
import com.ondemandmonitoring.user.dto.response.UserProfileResponse;
import com.ondemandmonitoring.user.service.UserProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserProfileService userProfileService;

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserProfileResponse>> getCurrentProfile() {
        return ResponseEntity.ok(ApiResponse.ok(userProfileService.getCurrentProfile()));
    }

    @PatchMapping("/me")
    public ResponseEntity<ApiResponse<UserProfileResponse>> updateCurrentProfile(
            @Valid @RequestBody UserProfileUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                "Profile updated successfully",
                userProfileService.updateCurrentProfile(request)));
    }
}
