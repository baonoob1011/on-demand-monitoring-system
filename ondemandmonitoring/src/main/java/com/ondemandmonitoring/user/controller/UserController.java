package com.ondemandmonitoring.user.controller;

import com.ondemandmonitoring.common.api.ApiResponse;
import com.ondemandmonitoring.user.dto.request.UserProfileUpdateRequest;
import com.ondemandmonitoring.user.dto.response.UserProfileResponse;
import com.ondemandmonitoring.user.service.IUserProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "User Profile", description = "APIs for managing current user profile")
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class UserController {

    IUserProfileService userProfileService;

    @Operation(summary = "Get current user profile", description = "Retrieves profile details of currently authenticated user")
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserProfileResponse>> getCurrentProfile() {
        return ResponseEntity.ok(ApiResponse.ok(userProfileService.getCurrentProfile()));
    }

    @Operation(summary = "Update current user profile", description = "Updates profile information of currently authenticated user")
    @PatchMapping("/me")
    public ResponseEntity<ApiResponse<UserProfileResponse>> updateCurrentProfile(
            @Valid @RequestBody UserProfileUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                "Profile updated successfully",
                userProfileService.updateCurrentProfile(request)));
    }
}
