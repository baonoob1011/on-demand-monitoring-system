package com.ondemandmonitoring.auth.service;

import com.ondemandmonitoring.auth.dto.request.SocialSyncRequest;
import com.ondemandmonitoring.auth.dto.response.AuthResponse;
import com.ondemandmonitoring.auth.enumeration.AuthProvider;
import com.ondemandmonitoring.auth.enumeration.SocialAuthIntent;
import com.ondemandmonitoring.auth.port.out.SocialAuthenticationResult;
import com.ondemandmonitoring.auth.port.out.SocialIdentityProviderPort;
import com.ondemandmonitoring.auth.port.out.IdentityProviderPort;
import com.ondemandmonitoring.auth.infrastructure.outbox.AuthOutboxService;
import com.ondemandmonitoring.common.exception.ApiException;
import com.ondemandmonitoring.common.exception.ErrorCode;
import com.ondemandmonitoring.user.domain.User;
import com.ondemandmonitoring.user.dto.response.UserProfileResponse;
import com.ondemandmonitoring.user.enumeration.UserRole;
import com.ondemandmonitoring.user.service.IUserService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
@RequiredArgsConstructor
public class SocialAuthService {

    private final SocialIdentityProviderPort identityProvider;
    private final IdentityProviderPort cognitoUserDirectory;
    private final AuthOutboxService outboxService;
    private final IUserService userService;
    private final RefreshTokenCookieService refreshTokenCookieService;

    @Transactional
    public AuthResponse sync(SocialSyncRequest request, HttpServletResponse response) {
        validateProvider(request.getProvider());
        SocialAuthenticationResult identity;
        try {
            identity = identityProvider.exchangeSocialCode(request);
        } catch (IllegalStateException exception) {
            throw new ApiException(ErrorCode.AUTH_PROVIDER_ERROR, "Social authentication failed");
        }

        if (!identity.emailVerified() || identity.email() == null || identity.email().isBlank()) {
            throw new ApiException(ErrorCode.SOCIAL_EMAIL_NOT_VERIFIED);
        }

        String email = identity.email().trim().toLowerCase(Locale.ROOT);
        var existingUser = userService.findOptionalByEmail(email);
        boolean accountCreated = existingUser.isEmpty();
        User user;
        try {
            user = existingUser
                    .map(existing -> userService.syncExternalIdentity(
                            existing,
                            identity.username(),
                            identity.subject(),
                            identity.fullName(),
                            true))
                    .orElseGet(() -> createUser(request, identity, email));

            if (!Boolean.TRUE.equals(user.getIsActive())) {
                throw new ApiException(ErrorCode.ACCOUNT_DISABLED);
            }

            cognitoUserDirectory.addUserToGroup(identity.username(), user.getRole().name());
        } catch (RuntimeException exception) {
            if (accountCreated) {
                outboxService.scheduleCognitoCleanup(identity.username(), identity.subject());
            }
            throw new ApiException(ErrorCode.AUTH_PROVIDER_ERROR, "Unable to synchronize account role");
        }

        userService.recordLogin(user.getId());
        if (identity.refreshToken() != null) {
            refreshTokenCookieService.write(response, identity.refreshToken(), identity.username());
        }

        UserProfileResponse profile = UserProfileResponse.builder()
                .id(user.getId())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .emailVerified(user.getEmailVerified())
                .role(user.getRole())
                .isActive(user.getIsActive())
                .build();

        return AuthResponse.builder()
                .accessToken(identity.accessToken())
                .expiresIn(identity.expiresIn())
                .user(profile)
                .build();
    }

    private User createUser(SocialSyncRequest request, SocialAuthenticationResult identity, String email) {
        if (request.getIntent() == SocialAuthIntent.LOGIN_ONLY) {
            throw new ApiException(ErrorCode.USER_NOT_FOUND);
        }
        return userService.createLocalUser(
                email,
                identity.fullName(),
                UserRole.CUSTOMER,
                identity.username(),
                identity.subject());
    }

    private void validateProvider(AuthProvider provider) {
        if (provider != AuthProvider.GOOGLE) {
            throw new ApiException(ErrorCode.SOCIAL_PROVIDER_UNSUPPORTED);
        }
    }
}
