package com.ondemandmonitoring.auth.service;

import com.ondemandmonitoring.auth.dto.request.ForgotPasswordRequest;
import com.ondemandmonitoring.auth.dto.request.ResetPasswordRequest;
import com.ondemandmonitoring.auth.port.out.IdentityProviderPort;
import com.ondemandmonitoring.common.exception.ApiException;
import com.ondemandmonitoring.common.exception.ErrorCode;
import com.ondemandmonitoring.user.service.IUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.cognitoidentityprovider.model.CodeMismatchException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.CognitoIdentityProviderException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.ExpiredCodeException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.InvalidParameterException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.InvalidPasswordException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UserNotFoundException;

import java.util.Locale;

@Service
@RequiredArgsConstructor
public class PasswordService {

    private final IUserService userService;
    private final IdentityProviderPort identityProvider;

    public void forgotPassword(ForgotPasswordRequest request) {
        String email = normalizeEmail(request.getEmail());
        if (userService.findOptionalByEmail(email).isEmpty()) {
            return;
        }
        try {
            identityProvider.forgotPassword(email);
        } catch (UserNotFoundException exception) {
            throw new ApiException(ErrorCode.USER_NOT_FOUND);
        } catch (InvalidParameterException exception) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "Tài khoản chưa bật local password");
        } catch (CognitoIdentityProviderException exception) {
            String message = exception.awsErrorDetails() == null
                    ? exception.getMessage() : exception.awsErrorDetails().errorMessage();
            throw new ApiException(ErrorCode.AUTH_PROVIDER_ERROR, message);
        }
    }

    public void resetPassword(ResetPasswordRequest request) {
        String email = normalizeEmail(request.getEmail());
        try {
            identityProvider.resetPassword(email, request.getOtpCode(), request.getNewPassword());
        } catch (CodeMismatchException exception) {
            throw new ApiException(ErrorCode.OTP_INVALID);
        } catch (ExpiredCodeException exception) {
            throw new ApiException(ErrorCode.OTP_EXPIRED);
        } catch (InvalidPasswordException exception) {
            throw new ApiException(ErrorCode.PASSWORD_POLICY_VIOLATED);
        } catch (UserNotFoundException exception) {
            throw new ApiException(ErrorCode.USER_NOT_FOUND);
        } catch (CognitoIdentityProviderException exception) {
            String message = exception.awsErrorDetails() == null
                    ? exception.getMessage() : exception.awsErrorDetails().errorMessage();
            throw new ApiException(ErrorCode.AUTH_PROVIDER_ERROR, message);
        }
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
