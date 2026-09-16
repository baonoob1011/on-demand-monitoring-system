package com.ondemandmonitoring.controlgateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.backend")
public record BackendProperties(String baseUrl, Duration timeout) {
    public BackendProperties {
        baseUrl = baseUrl == null || baseUrl.isBlank() ? "http://localhost:8080" : baseUrl;
        timeout = timeout == null ? Duration.ofSeconds(10) : timeout;
    }
}
