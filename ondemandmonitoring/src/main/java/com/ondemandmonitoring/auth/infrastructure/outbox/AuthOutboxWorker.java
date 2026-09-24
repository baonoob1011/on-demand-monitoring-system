package com.ondemandmonitoring.auth.infrastructure.outbox;

import com.ondemandmonitoring.auth.port.out.IdentityProviderPort;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AuthOutboxWorker {

    final AuthOutboxService outboxService;
    final IdentityProviderPort identityProvider;

    @Value("${auth.outbox.max-attempts:8}")
    int maxAttempts;

    @Scheduled(fixedDelayString = "${auth.outbox.fixed-delay-ms:30000}")
    public void processNext() {
        outboxService.claimNext().ifPresent(event -> {
            try {
                process(event);
                outboxService.markCompleted(event);
            } catch (Exception exception) {
                log.error("Auth outbox event {} failed on attempt {}", event.getId(), event.getAttempts(), exception);
                outboxService.markFailed(event, exception, maxAttempts);
            }
        });
    }

    private void process(AuthOutboxEvent event) {
        switch (event.getEventType()) {
            case AuthOutboxEvent.COGNITO_USER_CLEANUP ->
                    identityProvider.deleteUser(event.getTargetUsername());
            case AuthOutboxEvent.COGNITO_USER_ENABLE ->
                    identityProvider.enableUser(event.getTargetUsername());
            case AuthOutboxEvent.COGNITO_USER_DISABLE ->
                    identityProvider.disableUser(event.getTargetUsername());
            default -> throw new IllegalArgumentException(
                    "Unsupported auth outbox event type: " + event.getEventType());
        }
    }
}
