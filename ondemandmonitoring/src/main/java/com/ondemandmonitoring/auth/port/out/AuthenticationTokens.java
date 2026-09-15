package com.ondemandmonitoring.auth.port.out;

/** Tokens returned by the configured identity provider after authentication. */
public record AuthenticationTokens(
        String accessToken,
        String refreshToken,
        Integer expiresIn,
        String username,
        String challengeName,
        String session) {

    public AuthenticationTokens(String accessToken, String refreshToken,
                                Integer expiresIn, String username) {
        this(accessToken, refreshToken, expiresIn, username, null, null);
    }

    public static AuthenticationTokens challenge(String username, String challengeName, String session) {
        return new AuthenticationTokens(null, null, null, username, challengeName, session);
    }

    public boolean requiresChallenge() {
        return challengeName != null && session != null;
    }
}
