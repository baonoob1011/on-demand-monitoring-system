package com.ondemandmonitoring.auth.service;

import com.ondemandmonitoring.auth.dto.request.ForgotPasswordRequest;
import com.ondemandmonitoring.auth.dto.request.ResetPasswordRequest;
import com.ondemandmonitoring.auth.port.out.IdentityProviderPort;
import com.ondemandmonitoring.common.exception.ApiException;
import com.ondemandmonitoring.common.exception.ErrorCode;
import com.ondemandmonitoring.user.domain.User;
import com.ondemandmonitoring.user.enumeration.IdentityProvider;
import com.ondemandmonitoring.user.service.IUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.cognitoidentityprovider.model.ExpiredCodeException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.InvalidPasswordException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PasswordServiceTest {
    private IUserService users;
    private IdentityProviderPort identityProvider;
    private PasswordService service;

    @BeforeEach
    void setUp() {
        users = mock(IUserService.class);
        identityProvider = mock(IdentityProviderPort.class);
        service = new PasswordService(users, identityProvider);
    }

    @Test
    void forgotPasswordDoesNotRevealUnknownEmail() {
        ForgotPasswordRequest request = new ForgotPasswordRequest();
        request.setEmail("unknown@example.com");
        when(users.findOptionalByEmail(request.getEmail())).thenReturn(Optional.empty());

        service.forgotPassword(request);

        verifyNoInteractions(identityProvider);
    }

    @Test
    void forgotPasswordUsesLocalCognitoIdentity() {
        ForgotPasswordRequest request = new ForgotPasswordRequest();
        request.setEmail(" USER@Example.com ");
        when(users.findOptionalByEmail("user@example.com")).thenReturn(Optional.of(User.builder().build()));
        when(users.getCognitoUsername("user@example.com", IdentityProvider.LOCAL)).thenReturn("uuid-user");

        service.forgotPassword(request);

        verify(identityProvider).forgotPassword("uuid-user");
    }

    @Test
    void resetPasswordMapsExpiredCode() {
        ResetPasswordRequest request = resetRequest();
        when(users.getCognitoUsername(anyString(), any())).thenReturn("uuid-user");
        doThrow(ExpiredCodeException.builder().message("expired").build())
                .when(identityProvider).resetPassword(anyString(), anyString(), anyString());

        ApiException exception = assertThrows(ApiException.class, () -> service.resetPassword(request));
        assertEquals(ErrorCode.OTP_EXPIRED, exception.getErrorCode());
    }

    @Test
    void resetPasswordMapsPolicyViolation() {
        ResetPasswordRequest request = resetRequest();
        when(users.getCognitoUsername(anyString(), any())).thenReturn("uuid-user");
        doThrow(InvalidPasswordException.builder().message("weak").build())
                .when(identityProvider).resetPassword(anyString(), anyString(), anyString());

        ApiException exception = assertThrows(ApiException.class, () -> service.resetPassword(request));
        assertEquals(ErrorCode.PASSWORD_POLICY_VIOLATED, exception.getErrorCode());
    }

    private ResetPasswordRequest resetRequest() {
        ResetPasswordRequest request = new ResetPasswordRequest();
        request.setEmail("user@example.com");
        request.setOtpCode("123456");
        request.setNewPassword("NewPassword1!");
        return request;
    }
}
