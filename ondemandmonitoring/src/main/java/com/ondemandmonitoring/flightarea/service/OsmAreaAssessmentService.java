package com.ondemandmonitoring.flightarea.service;

import com.ondemandmonitoring.flightarea.client.OverpassClient;
import com.ondemandmonitoring.flightarea.client.OverpassClient.Category;
import com.ondemandmonitoring.flightarea.client.OverpassClient.OverpassData;
import com.ondemandmonitoring.flightarea.client.OverpassClient.RawFeature;
import com.ondemandmonitoring.flightarea.config.FlightAreaProperties;
import com.ondemandmonitoring.flightarea.dto.FlightAreaAssessmentResponse.NearbyFeature;
import com.ondemandmonitoring.flightarea.dto.FlightAreaAssessmentResponse.OsmContext;
import com.ondemandmonitoring.flightarea.support.ExternalProviderException;
import com.ondemandmonitoring.flightarea.support.GeoMath;
import com.ondemandmonitoring.flightarea.support.TtlCache;
import java.time.Clock;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Context around the monitoring point from OpenStreetMap. This is supplementary data only: it
 * never says a flight is allowed, and a building height is only reported when OSM carries one.
 */
@Service
@Slf4j
public class OsmAreaAssessmentService {

    public static final String SOURCE = "OPENSTREETMAP";

    private record Key(double latitude, double longitude, long radiusStep) {
    }

    private final OverpassClient client;
    private final FlightAreaProperties properties;
    private final TtlCache<Key, OverpassData> cache;

    @Autowired
    public OsmAreaAssessmentService(OverpassClient client, FlightAreaProperties properties) {
        this(client, properties, Clock.systemUTC());
    }

    public OsmAreaAssessmentService(OverpassClient client, FlightAreaProperties properties, Clock clock) {
        this.client = client;
        this.properties = properties;
        this.cache = new TtlCache<>(properties.getCache().isEnabled(), properties.getCache().getMaxEntries(), clock);
    }

    /** The radius actually queried: the customer's radius, capped by configuration. */
    public double queryRadius(double radiusMeters) {
        return Math.min(radiusMeters, properties.getOsm().getQueryMaxRadiusM());
    }

    public OsmContext assess(double latitude, double longitude, double radiusMeters) {
        double queryRadius = queryRadius(radiusMeters);
        // Rounded centre (~11 m) keeps repeated drags on the same cache entry; distances are measured from it.
        double lat = GeoMath.round(latitude, 4);
        double lon = GeoMath.round(longitude, 4);
        Key key = new Key(lat, lon, Math.round(queryRadius / 10.0));
        try {
            OverpassData data = cache.get(key).orElse(null);
            if (data == null) {
                data = client.fetch(lat, lon, queryRadius, properties.getOsm().getAerodromeSearchRadiusM());
                cache.put(key, data, Duration.ofMinutes(properties.getCache().getOsmTtlMinutes()));
            }
            return summarize(data, lat, lon, queryRadius);
        } catch (ExternalProviderException exception) {
            return unavailable(exception.getErrorCode());
        } catch (RuntimeException exception) {
            log.warn("Unexpected OSM assessment failure: {}", exception.toString());
            return unavailable(ExternalProviderException.PROVIDER_UNAVAILABLE);
        }
    }

    private OsmContext summarize(OverpassData data, double lat, double lon, double queryRadius) {
        int towers = 0;
        int masts = 0;
        int powerTowers = 0;
        boolean aerodrome = false;
        boolean helipad = false;
        for (RawFeature feature : data.features()) {
            switch (feature.category()) {
                case TOWER -> towers++;
                case MAST -> masts++;
                case POWER_TOWER -> powerTowers++;
                case AERODROME -> aerodrome = true;
                case HELIPAD -> helipad = true;
                case BUILDING -> { }
            }
        }
        List<NearbyFeature> important = data.features().stream()
                .map(feature -> toNearby(feature, lat, lon))
                .sorted(Comparator
                        .comparingInt((NearbyFeature feature) -> priority(feature.type()))
                        .thenComparing(NearbyFeature::distanceMeters))
                .limit(properties.getOsm().getMaxImportantFeatures())
                .toList();
        return new OsmContext(true, null, queryRadius, data.buildingCount(), towers, masts, powerTowers,
                aerodrome, helipad, important);
    }

    private static NearbyFeature toNearby(RawFeature feature, double lat, double lon) {
        double distance = GeoMath.haversineMeters(lat, lon, feature.latitude(), feature.longitude());
        return new NearbyFeature(feature.category().name(), feature.name(), Math.round(distance * 10) / 10.0,
                feature.heightMeters(), feature.latitude(), feature.longitude(), SOURCE);
    }

    /** Aerodromes and helipads first, then obstacles, nearest first within each group. */
    private static int priority(String type) {
        return Category.AERODROME.name().equals(type) || Category.HELIPAD.name().equals(type) ? 0 : 1;
    }

    private static OsmContext unavailable(String errorCode) {
        return new OsmContext(false, errorCode, null, 0, 0, 0, 0, false, false, List.of());
    }
}
