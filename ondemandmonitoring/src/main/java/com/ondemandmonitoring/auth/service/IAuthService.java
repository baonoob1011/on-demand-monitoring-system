package com.ondemandmonitoring.auth.service;

import com.ondemandmonitoring.auth.dto.request.*;
import com.ondemandmonitoring.auth.dto.response.AuthResponse;
import com.ondemandmonitoring.auth.dto.response.RegisterResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public interface IAuthService {

    RegisterResponse register(RegisterRequest request);

    void verifyOtp(VerifyOtpRequest request);

    void resendOtp(ResendOtpRequest request);

    AuthResponse login(LoginRequest request, HttpServletResponse response);

    AuthResponse completeFirstLogin(FirstLoginPasswordChangeRequest request,
                                    HttpServletResponse response);

    AuthResponse socialSync(SocialSyncRequest request, HttpServletResponse response);

    void linkLocalIdentity(String cognitoSub, String cognitoUsername, LinkLocalIdentityRequest request);

    AuthResponse refresh(HttpServletRequest request);

    void logout(String accessToken, HttpServletRequest request, HttpServletResponse response);

    void forgotPassword(ForgotPasswordRequest request);

    void resetPassword(ResetPasswordRequest request);

}
