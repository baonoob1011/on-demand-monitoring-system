package com.ondemandmonitoring.auth.infrastructure.outbox;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AuthOutboxServiceTest {
    private AuthOutboxRepository repository;
    private AuthOutboxService service;

    @BeforeEach
    void setUp() {
        repository = mock(AuthOutboxRepository.class);
        service = new AuthOutboxService(repository);
    }

    @Test
    void scheduleCreatesPendingCleanupEvent() {
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.scheduleCognitoCleanup("username", "sub");

        verify(repository).save(argThat(event ->
                event.getStatus() == AuthOutboxStatus.PENDING
                        && "COGNITO_USER_CLEANUP".equals(event.getEventType())
                        && "username".equals(event.getTargetUsername())));
    }

    @Test
    void scheduleAccountStatusCreatesEnableAndDisableEvents() {
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.scheduleAccountStatusSync("enabled-user", true);
        service.scheduleAccountStatusSync("disabled-user", false);

        verify(repository).save(argThat(event ->
                AuthOutboxEvent.COGNITO_USER_ENABLE.equals(event.getEventType())
                        && "enabled-user".equals(event.getTargetUsername())
                        && event.getStatus() == AuthOutboxStatus.PENDING));
        verify(repository).save(argThat(event ->
                AuthOutboxEvent.COGNITO_USER_DISABLE.equals(event.getEventType())
                        && "disabled-user".equals(event.getTargetUsername())
                        && event.getStatus() == AuthOutboxStatus.PENDING));
    }

    @Test
    void claimMovesPendingEventToProcessingAndIncrementsAttempts() {
        AuthOutboxEvent event = AuthOutboxEvent.cognitoCleanup("username", "sub");
        when(repository.findNextBatch(eq(AuthOutboxStatus.PENDING), any(), any(), any(Pageable.class)))
                .thenReturn(List.of(event));

        var claimed = service.claimNext().orElseThrow();

        assertEquals(AuthOutboxStatus.PROCESSING, claimed.getStatus());
        assertEquals(1, claimed.getAttempts());
        assertNotNull(claimed.getClaimedAt());
    }

    @Test
    void markFailedRetriesBeforeLimitAndTerminatesAtLimit() {
        AuthOutboxEvent event = AuthOutboxEvent.cognitoCleanup("username", "sub");
        event.setAttempts(1);
        service.markFailed(event, new RuntimeException("temporary"), 2);
        assertEquals(AuthOutboxStatus.PENDING, event.getStatus());
        assertNotNull(event.getNextAttemptAt());

        event.setAttempts(2);
        service.markFailed(event, new RuntimeException("terminal"), 2);
        assertEquals(AuthOutboxStatus.FAILED, event.getStatus());
        assertEquals("terminal", event.getLastError());
    }
}
