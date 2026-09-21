package com.ondemandmonitoring.user.controller;

import com.ondemandmonitoring.common.api.ApiResponse;
import com.ondemandmonitoring.common.api.PageResponse;
import com.ondemandmonitoring.role.domain.RoleCode;
import com.ondemandmonitoring.user.dto.response.UserManagementSummaryResponse;
import com.ondemandmonitoring.user.dto.response.UserManagementDetailResponse;
import com.ondemandmonitoring.user.dto.request.UserStatusUpdateRequest;
import com.ondemandmonitoring.user.service.IUserManagementService;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class UserManagementController {

    private final IUserManagementService userManagementService;

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<UserManagementSummaryResponse>>> getUsers(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) RoleCode role,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) Boolean emailVerified) {
        return ResponseEntity.ok(ApiResponse.ok(userManagementService.getUsers(
                pageable, search, role, active, emailVerified)));
    }

    @GetMapping("/{userId}")
    public ResponseEntity<ApiResponse<UserManagementDetailResponse>> getUser(
            @PathVariable UUID userId) {
        return ResponseEntity.ok(ApiResponse.ok(userManagementService.getUser(userId)));
    }

    @PatchMapping("/{userId}/status")
    public ResponseEntity<ApiResponse<UserManagementDetailResponse>> updateStatus(
            @PathVariable UUID userId,
            @Valid @RequestBody UserStatusUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                "Account status updated successfully",
                userManagementService.updateStatus(userId, request.getActive())));
    }
}
