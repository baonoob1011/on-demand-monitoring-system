package com.ondemandmonitoring.mapillary;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Backend-only Mapillary settings. The access token must never be returned to clients or logged. */
@Getter
@Setter
@ConfigurationProperties(prefix = "mapillary")
public class MapillaryProperties {

    private boolean enabled = true;
    private String baseUrl = "https://graph.mapillary.com";
    private String accessToken;
    private double searchRadiusMeters = 50;
    private int searchLimit = 5;
    private int connectTimeoutMs = 10000;
    private int readTimeoutMs = 20000;
    private long maxImageBytes = 10L * 1024 * 1024;

    public boolean hasToken() {
        return accessToken != null && !accessToken.isBlank();
    }
}
