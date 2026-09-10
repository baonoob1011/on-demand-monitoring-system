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
import com.ondemandmonitoring.role.domain.RoleCode;
import com.ondemandmonitoring.user.enumeration.IdentityProvider;
import com.ondemandmonitoring.user.service.IUserService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
@RequiredArgsConstructor
@Slf4j
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
        String effectiveCognitoUsername = identity.username();
        User user;
        try {
            if (existingUser.isPresent()) {
                User existing = existingUser.get();
            if (userService.hasIdentity(existing.getId(), IdentityProvider.LOCAL)) {
                effectiveCognitoUsername = userService.getCognitoUsername(email, IdentityProvider.LOCAL);
                }
                user = syncExistingSocialIdentity(existing, identity);
            } else {
                user = createUser(request, identity, email);
            }

            if (!Boolean.TRUE.equals(user.getIsActive())) {
                throw new ApiException(ErrorCode.ACCOUNT_DISABLED);
            }

            cognitoUserDirectory.addUserToGroup(effectiveCognitoUsername, user.getRole().getCode().name());
        } catch (RuntimeException exception) {
            if (exception instanceof ApiException) {
                throw exception;
            }
            log.error("Unable to synchronize social account. email={}, cognitoUsername={}, subject={}",
                    email, identity.username(), identity.subject(), exception);
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
                .role(user.getRole().getCode())
                .isActive(user.getIsActive())
                .build();

        return AuthResponse.builder()
                .accessToken(identity.accessToken())
                .expiresIn(identity.expiresIn())
                .user(profile)
                .build();
    }

    @Transactional
    public void linkLocalIdentity(String cognitoSub, String cognitoUsername, String password) {
        User user = userService.findByCognitoSub(cognitoSub);
        if (userService.hasIdentity(user.getId(), IdentityProvider.LOCAL)) {
            throw new ApiException(ErrorCode.RESOURCE_ALREADY_EXISTS,
                    "A local login is already linked to this account");
        }

        cognitoUserDirectory.setPermanentPassword(cognitoUsername, password);
        userService.linkLocalIdentity(user, cognitoUsername, cognitoSub);
    }

    private User syncExistingSocialIdentity(User user, SocialAuthenticationResult identity) {
        if (userService.hasIdentity(user.getId(), IdentityProvider.LOCAL)
                && !userService.hasIdentity(user.getId(), IdentityProvider.GOOGLE)) {
            if (identity.providerSubject() == null || identity.providerSubject().isBlank()) {
                throw new ApiException(ErrorCode.AUTH_PROVIDER_ERROR,
                        "Google provider subject is missing; the identity cannot be linked");
            }
            String localUsername = userService.getCognitoUsername(
                    user.getEmail(), IdentityProvider.LOCAL);
            if (!localUsername.equals(identity.username())) {
                cognitoUserDirectory.linkSocialIdentity(localUsername, "Google", identity.providerSubject());
            }
        }
        return userService.syncSocialIdentity(
                user,
                identity.username(),
                identity.subject(),
                identity.fullName(),
                true);
    }

    private User createUser(SocialSyncRequest request, SocialAuthenticationResult identity, String email) {
        if (request.getIntent() == SocialAuthIntent.LOGIN_ONLY) {
            throw new ApiException(ErrorCode.USER_NOT_FOUND);
        }
        return userService.createSocialUser(
                email,
                identity.fullName(),
                RoleCode.CUSTOMER,
                identity.username(),
                identity.subject());
    }

    private void validateProvider(AuthProvider provider) {
        if (provider != AuthProvider.GOOGLE) {
            throw new ApiException(ErrorCode.SOCIAL_PROVIDER_UNSUPPORTED);
        }
    }
}
