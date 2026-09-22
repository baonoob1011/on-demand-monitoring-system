package com.ondemandmonitoring.auth.controller;

import com.ondemandmonitoring.auth.dto.request.CreateManagedAccountRequest;
import com.ondemandmonitoring.auth.dto.response.ManagedAccountResponse;
import com.ondemandmonitoring.auth.service.IAdminAccountService;
import com.ondemandmonitoring.common.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin Account Management", description = "Admin APIs for creating and managing employee accounts")
@RestController
@RequestMapping("/api/admin/accounts")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AdminAccountController {

    IAdminAccountService adminAccountService;

    @Operation(summary = "Create managed account", description = "Invites and creates a new managed account for an employee role")
    @PostMapping
    public ResponseEntity<ApiResponse<ManagedAccountResponse>> create(
            @Valid @RequestBody CreateManagedAccountRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created("Account invitation sent.", adminAccountService.create(request)));
    }
}
