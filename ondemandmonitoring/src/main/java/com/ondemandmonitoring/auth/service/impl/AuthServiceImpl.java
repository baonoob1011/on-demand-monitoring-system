package com.ondemandmonitoring.auth.service.impl;


import com.ondemandmonitoring.auth.dto.request.*;
import com.ondemandmonitoring.auth.dto.response.AuthResponse;
import com.ondemandmonitoring.auth.dto.response.RegisterResponse;
import com.ondemandmonitoring.auth.service.IAuthService;
import com.ondemandmonitoring.auth.service.LoginService;
import com.ondemandmonitoring.auth.service.PasswordService;
import com.ondemandmonitoring.auth.service.RegisterService;
import com.ondemandmonitoring.auth.service.SocialAuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements IAuthService {

    private final LoginService loginService;
    private final RegisterService registerService;
    private final PasswordService passwordService;
    private final SocialAuthService socialAuthService;

    @Override
    public RegisterResponse register(RegisterRequest request) {
        return registerService.register(request);
    }

    @Override
    public void verifyOtp(VerifyOtpRequest request) {
        registerService.verifyOtp(request);
    }

    @Override
    public void resendOtp(ResendOtpRequest request) {
        registerService.resendOtp(request);
    }

    @Override
    public AuthResponse login(LoginRequest request, HttpServletResponse response) {
        return loginService.login(request, response);
    }

    @Override
    public AuthResponse completeFirstLogin(FirstLoginPasswordChangeRequest request,
                                           HttpServletResponse response) {
        return loginService.completeFirstLogin(request, response);
    }

    @Override
    public AuthResponse socialSync(SocialSyncRequest request, HttpServletResponse response) {
        return socialAuthService.sync(request, response);
    }

    @Override
    public void linkLocalIdentity(String cognitoSub, String cognitoUsername,
                                  LinkLocalIdentityRequest request) {
        socialAuthService.linkLocalIdentity(cognitoSub, cognitoUsername, request.getPassword());
    }

    @Override
    public AuthResponse refresh(HttpServletRequest request) {
        return loginService.refresh(request);
    }

    @Override
    public void logout(String accessToken, HttpServletRequest request, HttpServletResponse response) {
        loginService.logout(accessToken, request, response);
    }

    @Override
    public void forgotPassword(ForgotPasswordRequest request) {
        passwordService.forgotPassword(request);
    }

    @Override
    public void resetPassword(ResetPasswordRequest request) {
        passwordService.resetPassword(request);
    }
}
