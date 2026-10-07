package com.ondemandmonitoring.flightarea.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ondemandmonitoring.flightarea.config.FlightAreaProperties;
import com.ondemandmonitoring.flightarea.support.ExternalProviderException;
import com.ondemandmonitoring.flightarea.support.OsmHeightParser;
import com.ondemandmonitoring.flightarea.support.ProviderRestTemplates;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

/**
 * Overpass client. The query is generated here from numbers only; callers can neither supply a
 * query nor a URL. Buildings are only counted (no geometry is downloaded); a handful of
 * flight-relevant feature kinds are listed with a position.
 */
@Component
@Slf4j
public class OverpassClient {

    public enum Category { AERODROME, HELIPAD, TOWER, MAST, POWER_TOWER, BUILDING }

    public record RawFeature(
            Category category,
            String name,
            double latitude,
            double longitude,
            Double heightMeters) {
    }

    public record OverpassData(int buildingCount, List<RawFeature> features) {
    }

    private final RestTemplate restTemplate;
    private final FlightAreaProperties.Osm config;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    public OverpassClient(FlightAreaProperties properties) {
        this(buildRestTemplate(properties.getOsm()), properties);
    }

    /** Test seam: lets tests bind a mock server to the RestTemplate. */
    public OverpassClient(RestTemplate restTemplate, FlightAreaProperties properties) {
        this.restTemplate = restTemplate;
        this.config = properties.getOsm();
    }

    private static RestTemplate buildRestTemplate(FlightAreaProperties.Osm config) {
        return ProviderRestTemplates.create(config.getConnectTimeoutMs(), config.getReadTimeoutMs());
    }

    public OverpassData fetch(double latitude, double longitude, double radiusMeters, double aerodromeRadiusMeters) {
        String query = buildQuery(latitude, longitude, radiusMeters, aerodromeRadiusMeters);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.set(HttpHeaders.ACCEPT, "application/json");
        headers.set(HttpHeaders.USER_AGENT, config.getUserAgent());
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("data", query);
        ExternalProviderException failure = null;
        int attempts = Math.max(1, config.getMaxAttempts());
        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
                String body = restTemplate.exchange(config.getOverpassUrl(), HttpMethod.POST,
                        new HttpEntity<>(form, headers), String.class).getBody();
                // An unusable answer is not retried: asking again would not change it.
                return parse(body);
            } catch (RestClientException exception) {
                log.warn("Overpass request failed (attempt {}/{}): {}", attempt, attempts,
                        summary(exception));
                failure = new ExternalProviderException(ExternalProviderException.PROVIDER_UNAVAILABLE,
                        "Overpass unavailable", exception);
                if (attempt < attempts) pause();
            }
        }
        throw failure;
    }

    /** One short line; Overpass error pages are whole HTML documents and must not flood the log. */
    private static String summary(RestClientException exception) {
        String message = String.valueOf(exception.getMessage());
        String line = message.lines().findFirst().orElse(message);
        return line.length() > 160 ? line.substring(0, 160) + "..." : line;
    }

    private void pause() {
        if (config.getRetryDelayMs() <= 0) return;
        try {
            Thread.sleep(config.getRetryDelayMs());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    String buildQuery(double latitude, double longitude, double radiusMeters, double aerodromeRadiusMeters) {
        String around = String.format(Locale.ROOT, "(around:%.0f,%.6f,%.6f)", radiusMeters, latitude, longitude);
        String aerodromeAround = String.format(Locale.ROOT, "(around:%.0f,%.6f,%.6f)",
                aerodromeRadiusMeters, latitude, longitude);
        int limit = config.getMaxElementsPerSection();
        return String.join("\n",
                String.format(Locale.ROOT, "[out:json][timeout:%d];", config.getServerTimeoutSeconds()),
                "(way[\"building\"]" + around + ";relation[\"building\"]" + around + ";);",
                "out count;",
                "way[\"building\"][\"height\"]" + around + ";",
                "out tags center " + limit + ";",
                "nwr[\"man_made\"~\"^(tower|mast)$\"]" + around + ";",
                "out tags center " + limit + ";",
                "node[\"power\"=\"tower\"]" + around + ";",
                "out tags center " + limit + ";",
                "nwr[\"aeroway\"~\"^(aerodrome|helipad)$\"]" + aerodromeAround + ";",
                "out tags center " + limit + ";");
    }

    private OverpassData parse(String body) {
        try {
            JsonNode elements = objectMapper.readTree(body == null ? "{}" : body).path("elements");
            if (!elements.isArray()) {
                throw new ExternalProviderException(ExternalProviderException.PROVIDER_BAD_RESPONSE,
                        "Overpass response has no elements");
            }
            int buildingCount = 0;
            boolean countSeen = false;
            Map<String, RawFeature> features = new LinkedHashMap<>();
            for (JsonNode element : elements) {
                String type = element.path("type").asText("");
                if ("count".equals(type)) {
                    if (!countSeen) {
                        buildingCount = element.path("tags").path("total").asInt(0);
                        countSeen = true;
                    }
                    continue;
                }
                JsonNode tags = element.path("tags");
                Category category = classify(tags);
                if (category == null) continue;
                JsonNode position = element.has("center") ? element.path("center") : element;
                double lat = position.path("lat").asDouble(Double.NaN);
                double lon = position.path("lon").asDouble(Double.NaN);
                if (!Double.isFinite(lat) || !Double.isFinite(lon)) continue;
                String name = tags.path("name").asText(null);
                Double height = OsmHeightParser.parseMeters(tags.path("height").asText(null));
                features.putIfAbsent(type + "/" + element.path("id").asText(),
                        new RawFeature(category, name, lat, lon, height));
            }
            if (!countSeen) {
                throw new ExternalProviderException(ExternalProviderException.PROVIDER_BAD_RESPONSE,
                        "Overpass response has no building count");
            }
            return new OverpassData(buildingCount, new ArrayList<>(features.values()));
        } catch (ExternalProviderException exception) {
            throw exception;
        } catch (Exception exception) {
            log.warn("Overpass response could not be parsed: {}", exception.getMessage());
            throw new ExternalProviderException(ExternalProviderException.PROVIDER_BAD_RESPONSE,
                    "Overpass returned an unusable response", exception);
        }
    }

    private static Category classify(JsonNode tags) {
        String aeroway = tags.path("aeroway").asText("");
        if ("aerodrome".equals(aeroway)) return Category.AERODROME;
        if ("helipad".equals(aeroway)) return Category.HELIPAD;
        String manMade = tags.path("man_made").asText("");
        if ("tower".equals(manMade)) return Category.TOWER;
        if ("mast".equals(manMade)) return Category.MAST;
        if ("tower".equals(tags.path("power").asText(""))) return Category.POWER_TOWER;
        if (tags.has("building") && tags.has("height")) return Category.BUILDING;
        return null;
    }
}
