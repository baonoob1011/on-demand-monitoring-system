package com.ondemandmonitoring.auth.port.out;

/** Verified identity and tokens returned by a social Cognito/OIDC flow. */
public record SocialAuthenticationResult(
        String accessToken,
        String refreshToken,
        Integer expiresIn,
        String username,
        String subject,
        String providerSubject,
        String email,
        String fullName,
        boolean emailVerified) {
}
