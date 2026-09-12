package com.ondemandmonitoring.auth.infrastructure.outbox;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "auth_outbox_events", indexes = {
        @Index(name = "idx_auth_outbox_pending", columnList = "status,next_attempt_at,created_at")
})
@Getter
@Setter
@NoArgsConstructor
public class AuthOutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AuthOutboxStatus status;

    @Column(name = "event_type", nullable = false, length = 80)
    private String eventType;

    @Column(name = "target_username", nullable = false, length = 128)
    private String targetUsername;

    @Column(name = "target_cognito_sub", length = 64)
    private String targetCognitoSub;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "claimed_at")
    private Instant claimedAt;

    @Column(name = "processed_at")
    private Instant processedAt;

    @Column(name = "last_error", length = 2000)
    private String lastError;

    @Version
    private long version;

    public static AuthOutboxEvent cognitoCleanup(String username, String cognitoSub) {
        AuthOutboxEvent event = new AuthOutboxEvent();
        event.status = AuthOutboxStatus.PENDING;
        event.eventType = "COGNITO_USER_CLEANUP";
        event.targetUsername = username;
        event.targetCognitoSub = cognitoSub;
        event.nextAttemptAt = Instant.now();
        event.createdAt = Instant.now();
        return event;
    }
}
