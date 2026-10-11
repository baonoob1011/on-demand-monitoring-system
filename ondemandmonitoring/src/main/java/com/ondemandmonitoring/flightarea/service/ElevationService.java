package com.ondemandmonitoring.flightarea.service;

import com.ondemandmonitoring.flightarea.client.ElevationClient;
import com.ondemandmonitoring.flightarea.config.FlightAreaProperties;
import com.ondemandmonitoring.flightarea.support.ExternalProviderException;
import com.ondemandmonitoring.flightarea.support.GeoMath;
import com.ondemandmonitoring.flightarea.support.TtlCache;
import java.time.Clock;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Terrain elevation above mean sea level (AMSL). A provider failure never throws: the result is
 * simply marked unavailable so the rest of the assessment can still be produced.
 */
@Service
@Slf4j
public class ElevationService {

    public static final String REFERENCE_AMSL = "AMSL";

    /** Internal model; {@code elevationMeters} is null when {@code available} is false. */
    public record ElevationResult(
            boolean available,
            Double elevationMeters,
            String reference,
            String provider,
            String errorCode) {
    }

    private record Key(double latitude, double longitude) {
    }

    private final ElevationClient client;
    private final FlightAreaProperties properties;
    private final TtlCache<Key, Double> cache;

    @Autowired
    public ElevationService(ElevationClient client, FlightAreaProperties properties) {
        this(client, properties, Clock.systemUTC());
    }

    public ElevationService(ElevationClient client, FlightAreaProperties properties, Clock clock) {
        this.client = client;
        this.properties = properties;
        this.cache = new TtlCache<>(properties.getCache().isEnabled(), properties.getCache().getMaxEntries(), clock);
    }

    public ElevationResult lookup(double latitude, double longitude) {
        String provider = properties.getElevation().getProviderName();
        // The terrain model is ~90 m resolution, so 4 decimals (~11 m) loses nothing meaningful.
        Key key = new Key(GeoMath.round(latitude, 4), GeoMath.round(longitude, 4));
        var cached = cache.get(key);
        if (cached.isPresent()) {
            return new ElevationResult(true, cached.get(), REFERENCE_AMSL, provider, null);
        }
        try {
            double meters = client.fetchElevationMeters(key.latitude(), key.longitude());
            cache.put(key, meters, Duration.ofMinutes(properties.getCache().getElevationTtlMinutes()));
            return new ElevationResult(true, meters, REFERENCE_AMSL, provider, null);
        } catch (ExternalProviderException exception) {
            return unavailable(provider, exception.getErrorCode());
        } catch (RuntimeException exception) {
            log.warn("Unexpected elevation failure: {}", exception.toString());
            return unavailable(provider, ExternalProviderException.PROVIDER_UNAVAILABLE);
        }
    }

    private static ElevationResult unavailable(String provider, String errorCode) {
        return new ElevationResult(false, null, REFERENCE_AMSL, provider, errorCode);
    }
}
