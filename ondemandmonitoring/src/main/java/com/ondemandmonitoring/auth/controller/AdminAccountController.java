package com.ondemandmonitoring.auth.controller;

import com.ondemandmonitoring.auth.dto.request.CreateManagedAccountRequest;
import com.ondemandmonitoring.auth.dto.response.ManagedAccountResponse;
import com.ondemandmonitoring.auth.service.IAdminAccountService;
import com.ondemandmonitoring.common.api.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/accounts")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminAccountController {

    private final IAdminAccountService adminAccountService;

    @PostMapping
    public ResponseEntity<ApiResponse<ManagedAccountResponse>> create(
            @Valid @RequestBody CreateManagedAccountRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created("Account invitation sent.", adminAccountService.create(request)));
    }
}
