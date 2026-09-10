package com.ondemandmonitoring.auth.service;

import com.ondemandmonitoring.auth.dto.request.RegisterRequest;
import com.ondemandmonitoring.auth.dto.request.ResendOtpRequest;
import com.ondemandmonitoring.auth.dto.request.VerifyOtpRequest;
import com.ondemandmonitoring.auth.dto.response.RegisterResponse;
import com.ondemandmonitoring.auth.port.out.IdentityProviderPort;
import com.ondemandmonitoring.auth.infrastructure.outbox.AuthOutboxService;
import com.ondemandmonitoring.common.exception.ApiException;
import com.ondemandmonitoring.common.exception.ErrorCode;
import com.ondemandmonitoring.user.service.IUserService;
import com.ondemandmonitoring.role.domain.RoleCode;
import com.ondemandmonitoring.user.enumeration.IdentityProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.cognitoidentityprovider.model.CognitoIdentityProviderException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.CodeMismatchException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.ExpiredCodeException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.InvalidPasswordException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.NotAuthorizedException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UserNotFoundException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UsernameExistsException;

import java.util.Locale;

@Service
@RequiredArgsConstructor
public class RegisterService {

    private final IUserService userService;
    private final IdentityProviderPort identityProvider;
    private final AuthOutboxService outboxService;

    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        String email = normalizeEmail(request.getEmail());
        request.setEmail(email);

        if (userService.findOptionalByEmail(email).isPresent()) {
            throw new ApiException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }

        try {
            String cognitoSub = identityProvider.signUp(request);
            try {
                userService.createLocalUser(
                        email,
                        request.getFullName(),
                        RoleCode.CUSTOMER,
                        cognitoSub,
                        cognitoSub);
                identityProvider.addUserToGroup(cognitoSub, RoleCode.CUSTOMER.name());
            } catch (RuntimeException exception) {
                outboxService.scheduleCognitoCleanup(cognitoSub, cognitoSub);
                throw exception;
            }
            return RegisterResponse.builder().otpRequired(true).build();
        } catch (UsernameExistsException exception) {
            throw new ApiException(ErrorCode.EMAIL_ALREADY_EXISTS);
        } catch (InvalidPasswordException exception) {
            throw new ApiException(ErrorCode.PASSWORD_POLICY_VIOLATED);
        } catch (CognitoIdentityProviderException exception) {
            throw providerError(exception);
        }
    }

    public void verifyOtp(VerifyOtpRequest request) {
        String email = normalizeEmail(request.getEmail());
        String username = userService.getCognitoUsername(email, IdentityProvider.LOCAL);
        try {
            identityProvider.confirmSignUp(username, request.getOtpCode());
            userService.markEmailVerified(email);
        } catch (CodeMismatchException exception) {
            throw new ApiException(ErrorCode.OTP_INVALID);
        } catch (ExpiredCodeException exception) {
            throw new ApiException(ErrorCode.OTP_EXPIRED);
        } catch (NotAuthorizedException exception) {
            throw new ApiException(ErrorCode.USER_ALREADY_CONFIRMED);
        } catch (UserNotFoundException exception) {
            throw new ApiException(ErrorCode.USER_NOT_FOUND);
        } catch (CognitoIdentityProviderException exception) {
            throw providerError(exception);
        }
    }

    public void resendOtp(ResendOtpRequest request) {
        String email = normalizeEmail(request.getEmail());
        String username = userService.getCognitoUsername(email, IdentityProvider.LOCAL);
        try {
            identityProvider.resendConfirmationCode(username);
        } catch (UserNotFoundException exception) {
            throw new ApiException(ErrorCode.USER_NOT_FOUND);
        } catch (NotAuthorizedException exception) {
            throw new ApiException(ErrorCode.USER_ALREADY_CONFIRMED);
        } catch (CognitoIdentityProviderException exception) {
            throw providerError(exception);
        }
    }

    private ApiException providerError(CognitoIdentityProviderException exception) {
        String message = exception.awsErrorDetails() == null
                ? exception.getMessage() : exception.awsErrorDetails().errorMessage();
        return new ApiException(ErrorCode.AUTH_PROVIDER_ERROR, message);
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
