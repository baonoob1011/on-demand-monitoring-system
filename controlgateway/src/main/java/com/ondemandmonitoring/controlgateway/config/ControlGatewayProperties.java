package com.ondemandmonitoring.controlgateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.control-gateway")
public record ControlGatewayProperties(
        FlightController flightController,
        Duration commandTimeout,
        Duration streamTimeout
) {
    public ControlGatewayProperties {
        flightController = flightController == null
                ? new FlightController("127.0.0.1", 50051, false, "")
                : flightController;
        commandTimeout = commandTimeout == null ? Duration.ofSeconds(15) : commandTimeout;
        streamTimeout = streamTimeout == null ? Duration.ofMinutes(15) : streamTimeout;
    }

    public record FlightController(String host, int port, boolean tls, String token) {
        public FlightController {
            host = host == null || host.isBlank() ? "127.0.0.1" : host;
            port = port <= 0 ? 50051 : port;
            token = token == null ? "" : token;
        }
    }
}
