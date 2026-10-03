package com.ondemandmonitoring.mapillary;

import com.ondemandmonitoring.common.exception.ApiException;
import com.ondemandmonitoring.common.exception.ErrorCode;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Thin HTTP client for the Mapillary Graph API. The token is sent in the Authorization header and is
 * never placed in a URL or written to logs.
 */
@Slf4j
@Component
@EnableConfigurationProperties(MapillaryProperties.class)
public class MapillaryClient {

    private static final String FIELDS =
            "id,geometry,captured_at,compass_angle,is_pano,thumb_2048_url,thumb_1024_url";

    private final MapillaryProperties properties;
    private final HttpClient httpClient;
    private final ObjectMapper mapper = JsonMapper.builder().build();

    public MapillaryClient(MapillaryProperties properties) {
        this.properties = properties;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(properties.getConnectTimeoutMs()))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    public List<MapillaryImageCandidate> searchNearby(
            double latitude, double longitude, double radiusMeters, int limit) {
        requireConfigured();
        String url = properties.getBaseUrl().replaceAll("/+$", "") + "/images"
                + "?fields=" + FIELDS
                + "&bbox=" + bbox(latitude, longitude, radiusMeters)
                + "&limit=" + limit;
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofMillis(properties.getReadTimeoutMs()))
                .header("Authorization", "OAuth " + properties.getAccessToken())
                .header("Accept", "application/json")
                .GET()
                .build();
        try {
            HttpResponse<String> response = sendWithOneRetry(request);
            if (response.statusCode() / 100 != 2) {
                String body = response.body() == null ? "" : response.body();
                log.warn("Mapillary search failed with HTTP {}: {}", response.statusCode(),
                        body.substring(0, Math.min(body.length(), 300)));
                throw unavailable();
            }
            return parse(response.body());
        } catch (IOException exception) {
            log.warn("Mapillary search failed: {}", exception.getClass().getSimpleName());
            throw unavailable();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw unavailable();
        }
    }

    /** Mapillary can be slow on first contact; retry once on timeout before giving up. */
    private HttpResponse<String> sendWithOneRetry(HttpRequest request) throws IOException, InterruptedException {
        try {
            return httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (java.net.http.HttpTimeoutException exception) {
            log.warn("Mapillary search timed out; retrying once");
            return httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        }
    }

    List<MapillaryImageCandidate> parse(String body) {
        List<MapillaryImageCandidate> candidates = new ArrayList<>();
        JsonNode data = mapper.readTree(body == null ? "{}" : body).path("data");
        for (JsonNode item : data) {
            JsonNode coordinates = item.path("geometry").path("coordinates");
            String thumb = firstText(item, "thumb_2048_url", "thumb_1024_url");
            if (item.path("id").asText("").isBlank() || !coordinates.isArray()
                    || coordinates.size() < 2 || thumb == null) {
                continue;
            }
            Double compass = item.hasNonNull("compass_angle") ? item.path("compass_angle").asDouble() : null;
            Instant capturedAt = item.hasNonNull("captured_at")
                    ? Instant.ofEpochMilli(item.path("captured_at").asLong())
                    : null;
            candidates.add(new MapillaryImageCandidate(
                    item.path("id").asText(),
                    coordinates.get(1).asDouble(),
                    coordinates.get(0).asDouble(),
                    capturedAt,
                    compass,
                    item.path("is_pano").asBoolean(false),
                    thumb));
        }
        return candidates;
    }

    /** Downloads image bytes from a Mapillary CDN url (no token required). */
    public byte[] download(String imageUrl) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(imageUrl))
                .timeout(Duration.ofMillis(properties.getReadTimeoutMs()))
                .GET()
                .build();
        try {
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            byte[] body = response.body();
            if (response.statusCode() / 100 != 2 || body == null || body.length == 0
                    || body.length > properties.getMaxImageBytes()) {
                log.warn("Mapillary image download rejected. status={}", response.statusCode());
                throw unavailable();
            }
            return body;
        } catch (IOException exception) {
            log.warn("Mapillary image download failed: {}", exception.getClass().getSimpleName());
            throw unavailable();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw unavailable();
        }
    }

    /** minLon,minLat,maxLon,maxLat around the point, as required by the Mapillary images search. */
    static String bbox(double latitude, double longitude, double radiusMeters) {
        double dLat = radiusMeters / 111_320.0;
        double dLon = radiusMeters / (111_320.0 * Math.max(0.01, Math.cos(Math.toRadians(latitude))));
        return String.format(Locale.ROOT, "%.7f,%.7f,%.7f,%.7f",
                longitude - dLon, latitude - dLat, longitude + dLon, latitude + dLat);
    }

    private void requireConfigured() {
        if (!properties.isEnabled() || !properties.hasToken()) {
            log.warn("Mapillary not configured: enabled={} tokenPresent={}",
                    properties.isEnabled(), properties.hasToken());
            throw unavailable();
        }
    }

    private static String firstText(JsonNode node, String... names) {
        for (String name : names) {
            String value = node.path(name).asText("");
            if (!value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private static ApiException unavailable() {
        return new ApiException(ErrorCode.MAPILLARY_UNAVAILABLE);
    }
}
