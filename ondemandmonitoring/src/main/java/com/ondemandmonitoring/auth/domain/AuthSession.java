package com.ondemandmonitoring.auth.domain;

import java.time.Instant;

/** Authentication session model; persistence is added with the auth use cases. */
public record AuthSession(String userId, String tokenId, Instant expiresAt) {
}
