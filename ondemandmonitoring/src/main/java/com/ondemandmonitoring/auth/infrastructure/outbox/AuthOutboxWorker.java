package com.ondemandmonitoring.auth.infrastructure.outbox;

import com.ondemandmonitoring.auth.port.out.IdentityProviderPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuthOutboxWorker {

    private final AuthOutboxService outboxService;
    private final IdentityProviderPort identityProvider;

    @Value("${auth.outbox.max-attempts:8}")
    private int maxAttempts;

    @Scheduled(fixedDelayString = "${auth.outbox.fixed-delay-ms:30000}")
    public void processNext() {
        outboxService.claimNext().ifPresent(event -> {
            try {
                if ("COGNITO_USER_CLEANUP".equals(event.getEventType())) {
                    identityProvider.deleteUser(event.getTargetUsername());
                }
                outboxService.markCompleted(event);
            } catch (Exception exception) {
                log.error("Auth outbox event {} failed on attempt {}", event.getId(), event.getAttempts(), exception);
                outboxService.markFailed(event, exception, maxAttempts);
            }
        });
    }
}
