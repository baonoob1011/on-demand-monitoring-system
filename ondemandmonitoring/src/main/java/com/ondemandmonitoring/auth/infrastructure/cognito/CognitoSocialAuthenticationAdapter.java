package com.ondemandmonitoring.auth.infrastructure.cognito;

import com.ondemandmonitoring.auth.dto.request.SocialSyncRequest;
import com.ondemandmonitoring.auth.port.out.SocialAuthenticationResult;
import com.ondemandmonitoring.auth.port.out.SocialIdentityProviderPort;
import com.ondemandmonitoring.config.CognitoProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.HttpEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.RestTemplate;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Exchanges an OAuth authorization code and validates the resulting Cognito ID token. */
@Component
@RequiredArgsConstructor
@Slf4j
public class CognitoSocialAuthenticationAdapter implements SocialIdentityProviderPort {

    private static final Pattern PROVIDER_SUB_PATTERN = Pattern.compile(
            "\\\"userId\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"");

    private final RestTemplate restTemplate;
    private final CognitoProperties properties;

    @Qualifier("cognitoIdTokenDecoder")
    private final JwtDecoder cognitoIdTokenDecoder;

    @Override
    public SocialAuthenticationResult exchangeSocialCode(SocialSyncRequest request) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            headers.setBasicAuth(properties.clientId(), properties.clientSecret());

            MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("grant_type", "authorization_code");
            form.add("client_id", properties.clientId());
            form.add("code", request.getCode());
            form.add("redirect_uri", request.getRedirectUri());

            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    properties.tokenEndpoint(),
                    org.springframework.http.HttpMethod.POST,
                    new HttpEntity<>(form, headers),
                    new ParameterizedTypeReference<>() { });
            Map<String, Object> tokenResponse = response.getBody();

            if (tokenResponse == null
                    || tokenResponse.get("id_token") == null
                    || tokenResponse.get("access_token") == null) {
                throw new IllegalStateException("Cognito did not return an id_token");
            }

            String idToken = tokenResponse.get("id_token").toString();
            Jwt jwt = cognitoIdTokenDecoder.decode(idToken);
            String email = jwt.getClaimAsString("email");
            String subject = jwt.getSubject();
            String username = valueOrDefault(jwt.getClaimAsString("cognito:username"), subject);
            String providerSubject = socialProviderSubject(jwt);
            if (providerSubject == null) {
                providerSubject = googleProviderSubject(username);
            }
            String fullName = valueOrDefault(jwt.getClaimAsString("name"), email);
            boolean emailVerified = Boolean.parseBoolean(
                    String.valueOf(jwt.getClaims().getOrDefault("email_verified", false)));

            return new SocialAuthenticationResult(
                    tokenResponse.get("access_token").toString(),
                    tokenResponse.get("refresh_token") == null
                            ? null : tokenResponse.get("refresh_token").toString(),
                    ((Number) tokenResponse.getOrDefault("expires_in", 0)).intValue(),
                    username,
                    subject,
                    providerSubject,
                    email,
                    fullName,
                    emailVerified);
        } catch (RuntimeException exception) {
            log.error(
                    "Cognito social authentication exchange failed. endpoint={}, clientId={}, redirectUri={}",
                    properties.tokenEndpoint(),
                    properties.clientId(),
                    request.getRedirectUri(),
                    exception);
            throw new IllegalStateException("Social authentication exchange failed", exception);
        }
    }

    private String valueOrDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String googleProviderSubject(String username) {
        if (username == null) {
            return null;
        }
        int separator = username.indexOf('_');
        if (separator > 0 && separator < username.length() - 1
                && "google".equalsIgnoreCase(username.substring(0, separator))) {
            return username.substring(separator + 1);
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private String socialProviderSubject(Jwt jwt) {
        Object identities = jwt.getClaims().get("identities");
        if (identities instanceof Iterable<?> entries) {
            for (Object entry : entries) {
                if (entry instanceof Map<?, ?> identity) {
                    Object userId = identity.get("userId");
                    if (userId != null && !userId.toString().isBlank()) {
                        return userId.toString();
                    }
                }
            }
        }
        if (identities instanceof String identitiesJson) {
            Matcher matcher = PROVIDER_SUB_PATTERN.matcher(identitiesJson);
            if (matcher.find()) {
                return matcher.group(1);
            }
        }
        return null;
    }

}
