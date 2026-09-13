package com.ondemandmonitoring.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;

import java.util.Collection;

@Configuration
@EnableConfigurationProperties(CognitoProperties.class)
public class CognitoConfig {

    @Bean
    CognitoIdentityProviderClient cognitoIdentityProviderClient(
            CognitoProperties properties, Environment environment) {
        return CognitoIdentityProviderClient.builder()
                .region(Region.of(properties.region()))
                .credentialsProvider(credentialsProvider(environment))
                .httpClientBuilder(ApacheHttpClient.builder())
                .build();
    }

    private AwsCredentialsProvider credentialsProvider(Environment environment) {
        String accessKey = environment.getProperty("AWS_COGNITO_ACCESS_KEY_ID", "");
        String secretKey = environment.getProperty("AWS_COGNITO_SECRET_ACCESS_KEY", "");

        if (StringUtils.hasText(accessKey) && StringUtils.hasText(secretKey)) {
            return StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(accessKey, secretKey));
        }

        return DefaultCredentialsProvider.create();
    }

    @Bean
    RestTemplate restTemplate() {
        return new RestTemplate();
    }

    @Bean("cognitoIdTokenDecoder")
    JwtDecoder cognitoIdTokenDecoder(
            CognitoProperties properties,
            @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuerUri) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withIssuerLocation(issuerUri).build();
        OAuth2TokenValidator<Jwt> issuerValidator = JwtValidators.createDefaultWithIssuer(issuerUri);
        OAuth2TokenValidator<Jwt> tokenUseValidator = claimEqualsValidator(
                "token_use", "id", "The token is not a Cognito ID token");
        OAuth2TokenValidator<Jwt> audienceValidator = jwt -> {
            Object audienceClaim = jwt.getClaims().get("aud");
            boolean matches = audienceClaim instanceof Collection<?> audienceValues
                    ? audienceValues.stream().anyMatch(properties.clientId()::equals)
                    : properties.clientId().equals(audienceClaim);

            if (matches) {
                return OAuth2TokenValidatorResult.success();
            }

            return OAuth2TokenValidatorResult.failure(new OAuth2Error(
                    "invalid_token",
                    "The ID token audience does not match the Cognito app client",
                    null));
        };
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                issuerValidator,
                tokenUseValidator,
                audienceValidator));
        return decoder;
    }

    @Bean("cognitoAccessTokenDecoder")
    JwtDecoder cognitoAccessTokenDecoder(
            CognitoProperties properties,
            @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuerUri) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withIssuerLocation(issuerUri).build();
        OAuth2TokenValidator<Jwt> issuerValidator = JwtValidators.createDefaultWithIssuer(issuerUri);
        OAuth2TokenValidator<Jwt> tokenUseValidator = claimEqualsValidator(
                "token_use", "access", "The token is not a Cognito access token");
        OAuth2TokenValidator<Jwt> clientIdValidator = claimEqualsValidator(
                "client_id",
                properties.clientId(),
                "The access token client_id does not match the Cognito app client");

        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                issuerValidator,
                tokenUseValidator,
                clientIdValidator));
        return decoder;
    }

    private OAuth2TokenValidator<Jwt> claimEqualsValidator(
            String claimName, String expectedValue, String errorDescription) {
        return jwt -> expectedValue.equals(jwt.getClaimAsString(claimName))
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error(
                        "invalid_token",
                        errorDescription,
                        null));
    }

}
