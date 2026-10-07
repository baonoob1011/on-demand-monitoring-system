package com.ondemandmonitoring.flightarea.service;

import com.ondemandmonitoring.common.exception.ApiException;
import com.ondemandmonitoring.common.exception.ErrorCode;
import com.ondemandmonitoring.flightarea.config.FlightAreaProperties;
import com.ondemandmonitoring.flightarea.dto.FlightAreaAssessmentResponse;
import com.ondemandmonitoring.flightarea.dto.FlightAreaAssessmentResponse.Assessment;
import com.ondemandmonitoring.flightarea.dto.FlightAreaAssessmentResponse.OsmContext;
import com.ondemandmonitoring.flightarea.dto.FlightAreaAssessmentResponse.RestrictedZones;
import com.ondemandmonitoring.flightarea.service.ElevationService.ElevationResult;
import jakarta.annotation.PreDestroy;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Aggregates elevation, restricted-zone (PostGIS) and OpenStreetMap context into one preliminary
 * assessment. The two external providers run in parallel with the database query; a failure or
 * timeout of either only marks that part unavailable. The result is advisory and never changes an order.
 */
@Service
@Slf4j
public class FlightAreaAssessmentService {

    static final String PROVIDER_TIMEOUT = "PROVIDER_TIMEOUT";

    private final ElevationService elevationService;
    private final RestrictedZoneAssessmentService zoneService;
    private final OsmAreaAssessmentService osmService;
    private final FlightAreaRiskEvaluator evaluator;
    private final FlightAreaProperties properties;
    private final ExecutorService executor;

    @Autowired
    public FlightAreaAssessmentService(
            ElevationService elevationService,
            RestrictedZoneAssessmentService zoneService,
            OsmAreaAssessmentService osmService,
            FlightAreaRiskEvaluator evaluator,
            FlightAreaProperties properties) {
        this(elevationService, zoneService, osmService, evaluator, properties, Executors.newFixedThreadPool(4, runnable -> {
            Thread thread = new Thread(runnable, "flight-area-assessment");
            thread.setDaemon(true);
            return thread;
        }));
    }

    /** Test seam: lets tests supply a direct executor. */
    public FlightAreaAssessmentService(
            ElevationService elevationService,
            RestrictedZoneAssessmentService zoneService,
            OsmAreaAssessmentService osmService,
            FlightAreaRiskEvaluator evaluator,
            FlightAreaProperties properties,
            ExecutorService executor) {
        this.elevationService = elevationService;
        this.zoneService = zoneService;
        this.osmService = osmService;
        this.evaluator = evaluator;
        this.properties = properties;
        this.executor = executor;
    }

    @PreDestroy
    void shutdown() {
        executor.shutdown();
    }

    /** Plain strings so malformed values return 400, not 500 (same convention as the weather preview). */
    public FlightAreaAssessmentResponse assess(
            String latitudeText, String longitudeText, String radiusText, String altitudeText) {
        double latitude = parse(latitudeText, "latitude");
        double longitude = parse(longitudeText, "longitude");
        double radius = parse(radiusText, "radiusMeters");
        Double altitude = altitudeText == null || altitudeText.isBlank() ? null : parse(altitudeText, "requestedAltitudeAgl");
        validate(latitude, longitude, radius, altitude);
        return assess(latitude, longitude, radius, altitude);
    }

    public FlightAreaAssessmentResponse assess(double latitude, double longitude, double radius, Double altitudeAgl) {
        CompletableFuture<ElevationResult> elevationFuture =
                CompletableFuture.supplyAsync(() -> elevationService.lookup(latitude, longitude), executor);
        CompletableFuture<OsmContext> osmFuture =
                CompletableFuture.supplyAsync(() -> osmService.assess(latitude, longitude, radius), executor);

        // Internal data: runs on the request thread while the providers work.
        RestrictedZones zones = zoneService.assess(latitude, longitude, radius);

        ElevationResult elevation = await(elevationFuture,
                new ElevationResult(false, null, ElevationService.REFERENCE_AMSL,
                        properties.getElevation().getProviderName(), PROVIDER_TIMEOUT));
        OsmContext osm = await(osmFuture, new OsmContext(false, PROVIDER_TIMEOUT, null, 0, 0, 0, 0,
                false, false, List.of()));

        Assessment assessment = evaluator.evaluate(elevation, zones, osm, radius, altitudeAgl);
        return new FlightAreaAssessmentResponse(
                new FlightAreaAssessmentResponse.Location(latitude, longitude, radius),
                toElevation(elevation, altitudeAgl),
                zones,
                osm,
                assessment);
    }

    private static FlightAreaAssessmentResponse.Elevation toElevation(ElevationResult elevation, Double altitudeAgl) {
        Double estimated = elevation.available() && altitudeAgl != null
                ? Math.round((elevation.elevationMeters() + altitudeAgl) * 10) / 10.0
                : null;
        Double terrain = elevation.available() ? Math.round(elevation.elevationMeters() * 10) / 10.0 : null;
        return new FlightAreaAssessmentResponse.Elevation(
                elevation.available(), terrain, elevation.reference(), altitudeAgl, estimated,
                elevation.provider(), elevation.errorCode());
    }

    private <T> T await(CompletableFuture<T> future, T onTimeout) {
        try {
            return future.get(properties.getAggregateTimeoutMs(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException exception) {
            future.cancel(true);
            return onTimeout;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return onTimeout;
        } catch (Exception exception) {
            // The provider services already swallow provider errors; this is a last-resort guard.
            log.warn("Flight-area provider task failed: {}", exception.toString());
            return onTimeout;
        }
    }

    private void validate(double latitude, double longitude, double radius, Double altitude) {
        if (latitude < -90 || latitude > 90) invalid("latitude must be between -90 and 90");
        if (longitude < -180 || longitude > 180) invalid("longitude must be between -180 and 180");
        if (radius <= 0 || radius > properties.getMaxRadiusM()) {
            invalid("radiusMeters must be greater than 0 and at most " + (long) properties.getMaxRadiusM());
        }
        if (altitude != null && (altitude <= 0 || altitude > properties.getMaxAltitudeAglM())) {
            invalid("requestedAltitudeAgl must be greater than 0 and at most " + (long) properties.getMaxAltitudeAglM());
        }
    }

    private static double parse(String text, String name) {
        if (text == null || text.isBlank()) invalid(name + " is required");
        try {
            double value = Double.parseDouble(text.trim());
            if (!Double.isFinite(value)) invalid(name + " must be a finite number");
            return value;
        } catch (NumberFormatException exception) {
            invalid(name + " must be a number");
            return 0;
        }
    }

    private static void invalid(String message) {
        throw new ApiException(ErrorCode.INVALID_REQUEST, message);
    }
}
