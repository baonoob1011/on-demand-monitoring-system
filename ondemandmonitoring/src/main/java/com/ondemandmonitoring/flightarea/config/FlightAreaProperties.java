package com.ondemandmonitoring.flightarea.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Preliminary flight-area assessment settings (prefix {@code flight-area-assessment}).
 * Defaults live here so the feature works without any yaml/env entry. None of these values is a
 * legal or aviation limit; they only tune an advisory, rule-based preview.
 */
@Data
@Component
@ConfigurationProperties(prefix = "flight-area-assessment")
public class FlightAreaProperties {

    /** Largest monitoring radius the endpoint accepts. */
    private double maxRadiusM = 5000;
    /** Largest requested altitude above ground level the endpoint accepts. */
    private double maxAltitudeAglM = 500;
    /** A restricted zone closer than this (and not intersecting) raises a warning. */
    private double restrictedZoneWarningDistanceM = 500;
    /** Known building/tower height within this margin of the requested altitude raises a warning. */
    private double obstacleClearanceMarginM = 10;

    private Elevation elevation = new Elevation();
    private Osm osm = new Osm();
    private Cache cache = new Cache();
    /** Upper bound on how long the aggregate call waits for external providers. */
    private int aggregateTimeoutMs = 25000;

    @Data
    public static class Elevation {
        private String baseUrl = "https://api.open-meteo.com";
        private String path = "/v1/elevation";
        private String providerName = "Open-Meteo Elevation (Copernicus DEM)";
        private int connectTimeoutMs = 2000;
        private int readTimeoutMs = 4000;
    }

    @Data
    public static class Osm {
        private String overpassUrl = "https://overpass-api.de/api/interpreter";
        /** Per resolved address; Overpass has several and one may be unreachable from some networks. */
        private int connectTimeoutMs = 3000;
        /** Overpass is often slow (2-12 s even for tiny queries), so this is generous. */
        private int readTimeoutMs = 10000;
        /** Overpass server-side timeout, seconds. */
        private int serverTimeoutSeconds = 9;
        /** Transient failures (timeouts, 5xx such as "server too busy", 429) are retried up to this many tries. */
        private int maxAttempts = 2;
        private int retryDelayMs = 300;
        /** The Overpass query never uses a radius larger than this, whatever the customer picks. */
        private double queryMaxRadiusM = 1500;
        /** Aerodromes and helipads are searched this far from the monitoring centre. */
        private double aerodromeSearchRadiusM = 5000;
        /** At most this many important features are returned. */
        private int maxImportantFeatures = 15;
        /** Safety cap on elements requested per Overpass section. */
        private int maxElementsPerSection = 500;
        private String userAgent = "OnDemandMonitoring/1.0 (flight-area-assessment)";
    }

    @Data
    public static class Cache {
        private boolean enabled = true;
        private int elevationTtlMinutes = 1440;
        private int osmTtlMinutes = 60;
        private int maxEntries = 500;
    }
}
