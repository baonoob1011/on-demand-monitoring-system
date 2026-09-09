package com.ondemandmonitoring.auth.controller;

import com.ondemandmonitoring.auth.dto.request.*;
import com.ondemandmonitoring.auth.dto.response.AuthResponse;
import com.ondemandmonitoring.auth.dto.response.RegisterResponse;
import com.ondemandmonitoring.auth.service.IAuthService;
import com.ondemandmonitoring.common.api.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.web.csrf.CsrfToken;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final IAuthService authService;

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<RegisterResponse>> register(
            @Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created("Registration successful.", authService.register(request)));
    }

    @PostMapping("/verify-otp")
    public ResponseEntity<ApiResponse<Void>> verifyOtp(@Valid @RequestBody VerifyOtpRequest request) {
        authService.verifyOtp(request);
        return ResponseEntity.ok(ApiResponse.ok("Account verified successfully.", null));
    }

    @PostMapping("/resend-otp")
    public ResponseEntity<ApiResponse<Void>> resendOtp(@Valid @RequestBody ResendOtpRequest request) {
        authService.resendOtp(request);
        return ResponseEntity.ok(ApiResponse.ok("OTP code has been resent via email.", null));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(
            @Valid @RequestBody LoginRequest request, HttpServletResponse response) {
        return ResponseEntity.ok(ApiResponse.ok("Login successful.", authService.login(request, response)));
    }

    @PostMapping("/first-login/change-password")
    public ResponseEntity<ApiResponse<AuthResponse>> completeFirstLogin(
            @Valid @RequestBody FirstLoginPasswordChangeRequest request,
            HttpServletResponse response) {
        return ResponseEntity.ok(ApiResponse.ok("Password changed successfully.",
                authService.completeFirstLogin(request, response)));
    }

    @PostMapping("/social/sync")
    public ResponseEntity<ApiResponse<AuthResponse>> socialSync(
            @Valid @RequestBody SocialSyncRequest request, HttpServletResponse response) {
        return ResponseEntity.ok(ApiResponse.ok("Social login successful.",
                authService.socialSync(request, response)));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<AuthResponse>> refresh(HttpServletRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Token refreshed.", authService.refresh(request)));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader,
            HttpServletRequest request,
            HttpServletResponse response) {
        String accessToken = authorizationHeader != null && authorizationHeader.startsWith("Bearer ")
                ? authorizationHeader.substring(7)
                : authorizationHeader;
        authService.logout(accessToken, request, response);
        return ResponseEntity.ok(ApiResponse.ok("Logout successful.", null));
    }

    @GetMapping("/csrf")
    public ResponseEntity<ApiResponse<String>> csrf(CsrfToken token) {
        return ResponseEntity.ok(ApiResponse.ok("CSRF token generated.", token.getToken()));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<ApiResponse<Void>> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        authService.forgotPassword(request);
        return ResponseEntity.ok(ApiResponse.ok("Password reset OTP has been sent via email.", null));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<ApiResponse<Void>> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request);
        return ResponseEntity.ok(ApiResponse.ok("Password reset successful.", null));
    }
}
