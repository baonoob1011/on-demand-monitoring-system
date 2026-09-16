package com.ondemandmonitoring.controlgateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.security")
public record ControlSecurityProperties(
        String issuerUri,
        String clientId,
        String allowedOrigins
) {
    public ControlSecurityProperties {
        issuerUri = issuerUri == null ? "" : issuerUri;
        clientId = clientId == null ? "" : clientId;
        allowedOrigins = allowedOrigins == null || allowedOrigins.isBlank()
                ? "http://localhost:5173,http://127.0.0.1:5173"
                : allowedOrigins;
    }
}
