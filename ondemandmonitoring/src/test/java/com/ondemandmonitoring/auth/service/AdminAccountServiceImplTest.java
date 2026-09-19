package com.ondemandmonitoring.auth.service;

import com.ondemandmonitoring.auth.dto.request.CreateManagedAccountRequest;
import com.ondemandmonitoring.auth.infrastructure.outbox.AuthOutboxService;
import com.ondemandmonitoring.auth.port.out.IdentityProviderPort;
import com.ondemandmonitoring.auth.port.out.ManagedIdentity;
import com.ondemandmonitoring.auth.service.impl.AdminAccountServiceImpl;
import com.ondemandmonitoring.common.exception.ApiException;
import com.ondemandmonitoring.common.exception.ErrorCode;
import com.ondemandmonitoring.role.domain.RoleCode;
import com.ondemandmonitoring.user.service.IUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AdminAccountServiceImplTest {
    private IUserService users;
    private IdentityProviderPort identityProvider;
    private AuthOutboxService outbox;
    private AdminAccountServiceImpl service;

    @BeforeEach
    void setUp() {
        users = mock(IUserService.class);
        identityProvider = mock(IdentityProviderPort.class);
        outbox = mock(AuthOutboxService.class);
        service = new AdminAccountServiceImpl(users, identityProvider, outbox);
    }

    @Test
    void createsInvitedEmployeeAndAssignsMatchingGroup() {
        CreateManagedAccountRequest request = request(RoleCode.DRONE_OPERATOR);
        when(users.findOptionalByEmail("operator@example.com")).thenReturn(Optional.empty());
        when(identityProvider.createInvitedUser(request)).thenReturn(new ManagedIdentity("uuid-user", "sub"));

        var result = service.create(request);

        assertTrue(result.isInvitationSent());
        assertTrue(result.isPasswordChangeRequired());
        verify(users).createManagedUser("operator@example.com", "Operator", RoleCode.DRONE_OPERATOR,
                "uuid-user", "sub");
        verify(identityProvider).addUserToGroup("uuid-user", "DRONE_OPERATOR");
    }

    @Test
    void rejectsCustomerRole() {
        CreateManagedAccountRequest request = request(RoleCode.CUSTOMER);

        ApiException exception = assertThrows(ApiException.class, () -> service.create(request));

        assertEquals(ErrorCode.ACCESS_DENIED, exception.getErrorCode());
        verifyNoInteractions(identityProvider, users);
    }

    @Test
    void schedulesCleanupWhenManagedUserPersistenceFails() {
        CreateManagedAccountRequest request = request(RoleCode.STAFF);
        when(users.findOptionalByEmail(anyString())).thenReturn(Optional.empty());
        when(identityProvider.createInvitedUser(request)).thenReturn(new ManagedIdentity("uuid-user", "sub"));
        when(users.createManagedUser(anyString(), anyString(), any(), anyString(), anyString()))
                .thenThrow(new IllegalStateException("database unavailable"));

        assertThrows(IllegalStateException.class, () -> service.create(request));
        verify(outbox).scheduleCognitoCleanup("uuid-user", "sub");
    }

    private CreateManagedAccountRequest request(RoleCode role) {
        CreateManagedAccountRequest request = new CreateManagedAccountRequest();
        request.setEmail(" Operator@Example.com ");
        request.setFullName("Operator");
        request.setRole(role);
        return request;
    }
}
