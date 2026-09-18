package com.ondemandmonitoring.auth.service;

import com.ondemandmonitoring.auth.dto.request.SocialSyncRequest;
import com.ondemandmonitoring.auth.enumeration.AuthProvider;
import com.ondemandmonitoring.auth.enumeration.SocialAuthIntent;
import com.ondemandmonitoring.auth.infrastructure.outbox.AuthOutboxService;
import com.ondemandmonitoring.auth.port.out.AuthenticationTokens;
import com.ondemandmonitoring.auth.port.out.IdentityProviderPort;
import com.ondemandmonitoring.auth.port.out.SocialAuthenticationResult;
import com.ondemandmonitoring.auth.port.out.SocialIdentityProviderPort;
import com.ondemandmonitoring.common.exception.ApiException;
import com.ondemandmonitoring.common.exception.ErrorCode;
import com.ondemandmonitoring.role.domain.Role;
import com.ondemandmonitoring.role.domain.RoleCode;
import com.ondemandmonitoring.user.domain.User;
import com.ondemandmonitoring.user.enumeration.IdentityProvider;
import com.ondemandmonitoring.user.service.IUserService;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SocialAuthServiceTest {
    private SocialIdentityProviderPort socialProvider;
    private IdentityProviderPort cognito;
    private AuthOutboxService outbox;
    private IUserService users;
    private RefreshTokenCookieService cookies;
    private SocialAuthService service;

    @BeforeEach
    void setUp() {
        socialProvider = mock(SocialIdentityProviderPort.class);
        cognito = mock(IdentityProviderPort.class);
        outbox = mock(AuthOutboxService.class);
        users = mock(IUserService.class);
        cookies = mock(RefreshTokenCookieService.class);
        service = new SocialAuthService(socialProvider, cognito, outbox, users, cookies);
    }

    @Test
    void socialLoginCreatesCustomerAssignsGroupAndReturnsRefreshedToken() {
        SocialSyncRequest request = request(SocialAuthIntent.LOGIN_OR_REGISTER);
        SocialAuthenticationResult identity = identity(true);
        User user = customer();
        when(socialProvider.exchangeSocialCode(request)).thenReturn(identity);
        when(users.findOptionalByEmail("user@example.com")).thenReturn(Optional.empty());
        when(users.createSocialUser(anyString(), anyString(), eq(RoleCode.CUSTOMER), anyString(), anyString()))
                .thenReturn(user);
        when(cognito.refresh("social-refresh", "google_123"))
                .thenReturn(new AuthenticationTokens("access-with-group", null, 3600, "google_123"));
        HttpServletResponse response = mock(HttpServletResponse.class);

        var result = service.sync(request, response);

        assertEquals("access-with-group", result.getAccessToken());
        assertEquals(RoleCode.CUSTOMER, result.getUser().getRole());
        verify(cognito).addUserToGroup("google_123", "CUSTOMER");
        verify(cookies).write(response, "social-refresh", "google_123");
    }

    @Test
    void socialLoginLinksGoogleToExistingLocalUserWithoutChangingRole() {
        SocialSyncRequest request = request(SocialAuthIntent.LOGIN_OR_REGISTER);
        User staff = customer();
        staff.setRole(Role.builder().code(RoleCode.STAFF).build());
        when(socialProvider.exchangeSocialCode(request)).thenReturn(identity(true));
        when(users.findOptionalByEmail("user@example.com")).thenReturn(Optional.of(staff));
        when(users.hasIdentity(staff.getId(), IdentityProvider.LOCAL)).thenReturn(true);
        when(users.getCognitoUsername("user@example.com", IdentityProvider.LOCAL)).thenReturn("local-uuid");
        when(users.syncSocialIdentity(eq(staff), anyString(), anyString(), anyString(), eq(true))).thenReturn(staff);
        when(cognito.refresh("social-refresh", "local-uuid"))
                .thenReturn(new AuthenticationTokens("new-access", null, 3600, "local-uuid"));

        var result = service.sync(request, mock(HttpServletResponse.class));

        assertEquals(RoleCode.STAFF, result.getUser().getRole());
        verify(cognito).addUserToGroup("local-uuid", "STAFF");
        verify(users, never()).createSocialUser(anyString(), anyString(), any(), anyString(), anyString());
    }

    @Test
    void socialLoginRejectsUnverifiedEmail() {
        SocialSyncRequest request = request(SocialAuthIntent.LOGIN_OR_REGISTER);
        when(socialProvider.exchangeSocialCode(request)).thenReturn(identity(false));

        ApiException exception = assertThrows(ApiException.class,
                () -> service.sync(request, mock(HttpServletResponse.class)));

        assertEquals(ErrorCode.SOCIAL_EMAIL_NOT_VERIFIED, exception.getErrorCode());
        verifyNoInteractions(users, cognito);
    }

    @Test
    void loginOnlyDoesNotCreateUnknownSocialUser() {
        SocialSyncRequest request = request(SocialAuthIntent.LOGIN_ONLY);
        when(socialProvider.exchangeSocialCode(request)).thenReturn(identity(true));
        when(users.findOptionalByEmail("user@example.com")).thenReturn(Optional.empty());

        ApiException exception = assertThrows(ApiException.class,
                () -> service.sync(request, mock(HttpServletResponse.class)));

        assertEquals(ErrorCode.USER_NOT_FOUND, exception.getErrorCode());
        verify(users, never()).createSocialUser(anyString(), anyString(), any(), anyString(), anyString());
    }

    @Test
    void newSocialAccountSchedulesCleanupWhenRoleSynchronizationFails() {
        SocialSyncRequest request = request(SocialAuthIntent.LOGIN_OR_REGISTER);
        when(socialProvider.exchangeSocialCode(request)).thenReturn(identity(true));
        when(users.findOptionalByEmail("user@example.com")).thenReturn(Optional.empty());
        when(users.createSocialUser(anyString(), anyString(), any(), anyString(), anyString()))
                .thenReturn(customer());
        doThrow(new IllegalStateException("group failure"))
                .when(cognito).addUserToGroup("google_123", "CUSTOMER");

        ApiException exception = assertThrows(ApiException.class,
                () -> service.sync(request, mock(HttpServletResponse.class)));

        assertEquals(ErrorCode.AUTH_PROVIDER_ERROR, exception.getErrorCode());
        verify(outbox).scheduleCognitoCleanup("google_123", "cognito-sub");
    }

    @Test
    void linkLocalIdentityRejectsDuplicateLocalIdentity() {
        User user = customer();
        when(users.findByCognitoSub("sub")).thenReturn(user);
        when(users.hasIdentity(user.getId(), IdentityProvider.LOCAL)).thenReturn(true);

        ApiException exception = assertThrows(ApiException.class,
                () -> service.linkLocalIdentity("sub", "username", "Password1!"));

        assertEquals(ErrorCode.RESOURCE_ALREADY_EXISTS, exception.getErrorCode());
        verify(cognito, never()).setPermanentPassword(anyString(), anyString());
    }

    private SocialSyncRequest request(SocialAuthIntent intent) {
        SocialSyncRequest request = new SocialSyncRequest();
        request.setCode("code");
        request.setRedirectUri("http://localhost/callback");
        request.setProvider(AuthProvider.GOOGLE);
        request.setIntent(intent);
        return request;
    }

    private SocialAuthenticationResult identity(boolean verified) {
        return new SocialAuthenticationResult("initial-access", "social-refresh", 3600,
                "google_123", "cognito-sub", "123", " User@Example.com ", "User", verified);
    }

    private User customer() {
        return User.builder().id(UUID.randomUUID()).email("user@example.com").fullName("User")
                .emailVerified(true).isActive(true)
                .role(Role.builder().code(RoleCode.CUSTOMER).build()).build();
    }
}
