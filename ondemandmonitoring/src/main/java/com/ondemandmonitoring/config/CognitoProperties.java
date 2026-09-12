package com.ondemandmonitoring.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "aws.cognito")
public record CognitoProperties(
        String region,
        String userPoolId,
        String clientId,
        String clientSecret,
        String domain) {

    public String tokenEndpoint() {
        return domain.endsWith("/") ? domain + "oauth2/token" : domain + "/oauth2/token";
    }
}
