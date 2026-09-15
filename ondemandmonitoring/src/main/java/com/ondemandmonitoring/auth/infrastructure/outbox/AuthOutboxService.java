package com.ondemandmonitoring.auth.infrastructure.outbox;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AuthOutboxService {

    private final AuthOutboxRepository repository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void scheduleCognitoCleanup(String username, String cognitoSub) {
        repository.save(AuthOutboxEvent.cognitoCleanup(username, cognitoSub));
    }

    @Transactional
    public Optional<AuthOutboxEvent> claimNext() {
        Instant now = Instant.now();
        return repository.findNextBatch(AuthOutboxStatus.PENDING, now,
                        now.minus(Duration.ofMinutes(5)), PageRequest.of(0, 1))
                .stream()
                .findFirst()
                .map(event -> {
                    event.setStatus(AuthOutboxStatus.PROCESSING);
                    event.setAttempts(event.getAttempts() + 1);
                    event.setClaimedAt(now);
                    return event;
                });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markCompleted(AuthOutboxEvent event) {
        event.setStatus(AuthOutboxStatus.COMPLETED);
        event.setProcessedAt(Instant.now());
        event.setClaimedAt(null);
        repository.save(event);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(AuthOutboxEvent event, Exception exception, int maxAttempts) {
        event.setLastError(exception.getMessage());
        event.setClaimedAt(null);
        if (event.getAttempts() >= maxAttempts) {
            event.setStatus(AuthOutboxStatus.FAILED);
        } else {
            event.setStatus(AuthOutboxStatus.PENDING);
            event.setNextAttemptAt(Instant.now().plus(backoff(event.getAttempts())));
        }
        repository.save(event);
    }

    private Duration backoff(int attempts) {
        return Duration.ofSeconds(Math.min(300, Math.max(5, 5L * attempts * attempts)));
    }
}
