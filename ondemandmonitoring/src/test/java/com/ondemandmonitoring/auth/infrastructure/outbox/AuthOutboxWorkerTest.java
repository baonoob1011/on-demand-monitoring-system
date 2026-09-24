package com.ondemandmonitoring.auth.infrastructure.outbox;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ondemandmonitoring.auth.port.out.IdentityProviderPort;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class AuthOutboxWorkerTest {

    private AuthOutboxService outboxService;
    private IdentityProviderPort identityProvider;
    private AuthOutboxWorker worker;

    @BeforeEach
    void setUp() {
        outboxService = mock(AuthOutboxService.class);
        identityProvider = mock(IdentityProviderPort.class);
        worker = new AuthOutboxWorker(outboxService, identityProvider);
        ReflectionTestUtils.setField(worker, "maxAttempts", 3);
    }

    @Test
    void processNext_deletesUserForCleanupEvent() {
        AuthOutboxEvent event = AuthOutboxEvent.cognitoCleanup("cleanup-user", "sub");
        when(outboxService.claimNext()).thenReturn(Optional.of(event));

        worker.processNext();

        verify(identityProvider).deleteUser("cleanup-user");
        verify(outboxService).markCompleted(event);
    }

    @Test
    void processNext_enablesUserForEnableEvent() {
        AuthOutboxEvent event = AuthOutboxEvent.cognitoAccountStatus("enabled-user", true);
        when(outboxService.claimNext()).thenReturn(Optional.of(event));

        worker.processNext();

        verify(identityProvider).enableUser("enabled-user");
        verify(outboxService).markCompleted(event);
    }

    @Test
    void processNext_disablesUserForDisableEvent() {
        AuthOutboxEvent event = AuthOutboxEvent.cognitoAccountStatus("disabled-user", false);
        when(outboxService.claimNext()).thenReturn(Optional.of(event));

        worker.processNext();

        verify(identityProvider).disableUser("disabled-user");
        verify(outboxService).markCompleted(event);
    }

    @Test
    void processNext_marksFailedWhenProviderCallFails() {
        AuthOutboxEvent event = AuthOutboxEvent.cognitoAccountStatus("disabled-user", false);
        RuntimeException failure = new RuntimeException("Cognito unavailable");
        when(outboxService.claimNext()).thenReturn(Optional.of(event));
        org.mockito.Mockito.doThrow(failure)
                .when(identityProvider).disableUser("disabled-user");

        worker.processNext();

        verify(outboxService).markFailed(event, failure, 3);
        verify(outboxService, never()).markCompleted(event);
    }
}
