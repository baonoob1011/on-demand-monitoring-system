package com.ondemandmonitoring.flightarea.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ondemandmonitoring.flightarea.config.FlightAreaProperties;
import com.ondemandmonitoring.flightarea.support.ExternalProviderException;
import com.ondemandmonitoring.flightarea.support.ProviderRestTemplates;
import java.net.URI;
import java.util.Locale;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Thin elevation client (Open-Meteo Elevation API, no API key). The base URL comes from
 * configuration only; callers can never supply a URL.
 */
@Component
@Slf4j
public class ElevationClient {

    private final RestTemplate restTemplate;
    private final String baseUrl;
    private final String path;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    public ElevationClient(FlightAreaProperties properties) {
        this(buildRestTemplate(properties.getElevation()), properties);
    }

    /** Test seam: lets tests bind a mock server to the RestTemplate. */
    public ElevationClient(RestTemplate restTemplate, FlightAreaProperties properties) {
        this.restTemplate = restTemplate;
        this.baseUrl = properties.getElevation().getBaseUrl();
        this.path = properties.getElevation().getPath();
    }

    private static RestTemplate buildRestTemplate(FlightAreaProperties.Elevation config) {
        return ProviderRestTemplates.create(config.getConnectTimeoutMs(), config.getReadTimeoutMs());
    }

    /** Terrain elevation in metres above mean sea level at the coordinate. */
    public double fetchElevationMeters(double latitude, double longitude) {
        URI uri = UriComponentsBuilder.fromUriString(baseUrl)
                .path(path)
                .queryParam("latitude", String.format(Locale.ROOT, "%.6f", latitude))
                .queryParam("longitude", String.format(Locale.ROOT, "%.6f", longitude))
                .build()
                .toUri();
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.ACCEPT, "application/json");
        String body;
        try {
            body = restTemplate.exchange(uri, HttpMethod.GET, new HttpEntity<>(headers), String.class).getBody();
        } catch (RestClientException exception) {
            log.warn("Elevation request failed: {}", exception.getMessage());
            throw new ExternalProviderException(ExternalProviderException.PROVIDER_UNAVAILABLE,
                    "Elevation provider unavailable", exception);
        }
        return parse(body);
    }

    private double parse(String body) {
        try {
            JsonNode first = objectMapper.readTree(body == null ? "{}" : body).path("elevation").path(0);
            if (first.isNumber() && Double.isFinite(first.asDouble())) {
                return first.asDouble();
            }
        } catch (Exception exception) {
            log.warn("Elevation response could not be parsed: {}", exception.getMessage());
        }
        throw new ExternalProviderException(ExternalProviderException.PROVIDER_BAD_RESPONSE,
                "Elevation provider returned an unusable response");
    }
}
