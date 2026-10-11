package com.ondemandmonitoring.flightarea.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ondemandmonitoring.common.exception.GlobalExceptionHandler;
import com.ondemandmonitoring.flightarea.config.FlightAreaProperties;
import com.ondemandmonitoring.flightarea.dto.FlightAreaAssessmentResponse;
import com.ondemandmonitoring.flightarea.dto.FlightAreaAssessmentResponse.OsmContext;
import com.ondemandmonitoring.flightarea.dto.FlightAreaAssessmentResponse.RestrictedZones;
import com.ondemandmonitoring.flightarea.service.ElevationService;
import com.ondemandmonitoring.flightarea.service.ElevationService.ElevationResult;
import com.ondemandmonitoring.flightarea.service.FlightAreaAssessmentService;
import com.ondemandmonitoring.flightarea.service.FlightAreaRiskEvaluator;
import com.ondemandmonitoring.flightarea.service.OsmAreaAssessmentService;
import com.ondemandmonitoring.flightarea.service.RestrictedZoneAssessmentService;
import java.util.List;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** Real controller + real aggregate service + real evaluator; only the three data sources are mocked. */
class FlightAreaAssessmentControllerTest {

    private ElevationService elevation;
    private RestrictedZoneAssessmentService zones;
    private OsmAreaAssessmentService osm;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        elevation = mock(ElevationService.class);
        zones = mock(RestrictedZoneAssessmentService.class);
        osm = mock(OsmAreaAssessmentService.class);
        FlightAreaProperties properties = new FlightAreaProperties();
        var service = new FlightAreaAssessmentService(elevation, zones, osm, new FlightAreaRiskEvaluator(properties),
                properties, Executors.newFixedThreadPool(2));
        mvc = MockMvcBuilders.standaloneSetup(new FlightAreaAssessmentController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        when(elevation.lookup(anyDouble(), anyDouble()))
                .thenReturn(new ElevationResult(true, 18.2, "AMSL", "Open-Meteo", null));
        when(zones.assess(anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(new RestrictedZones(true, false, false, 850.0, List.of()));
        when(osm.assess(anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(new OsmContext(true, null, 300.0, 34, 1, 0, 2, false, false, List.of()));
    }

    private org.springframework.test.web.servlet.ResultActions call(String lat, String lon, String radius, String altitude)
            throws Exception {
        var request = get("/api/flight-area-assessment")
                .param("latitude", lat).param("longitude", lon).param("radiusMeters", radius);
        if (altitude != null) request.param("requestedAltitudeAgl", altitude);
        return mvc.perform(request);
    }

    @Test
    void returnsTheEnvelopeWithTheAggregateAssessment() throws Exception {
        call("10.6398245", "106.7172615", "300", "60")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.location.latitude").value(10.6398245))
                .andExpect(jsonPath("$.data.location.radiusMeters").value(300.0))
                .andExpect(jsonPath("$.data.elevation.available").value(true))
                .andExpect(jsonPath("$.data.elevation.terrainElevationMeters").value(18.2))
                .andExpect(jsonPath("$.data.elevation.reference").value("AMSL"))
                .andExpect(jsonPath("$.data.elevation.requestedAltitudeAglMeters").value(60.0))
                .andExpect(jsonPath("$.data.elevation.estimatedFlightAltitudeAmslMeters").value(78.2))
                .andExpect(jsonPath("$.data.restrictedZones.nearestRestrictedZoneDistanceMeters").value(850.0))
                .andExpect(jsonPath("$.data.osmContext.buildingCount").value(34))
                .andExpect(jsonPath("$.data.osmContext.powerTowerCount").value(2))
                .andExpect(jsonPath("$.data.assessment.level").value("FAVORABLE"))
                .andExpect(jsonPath("$.data.assessment.disclaimer").value(FlightAreaRiskEvaluator.DISCLAIMER));
    }

    @Test
    void theAltitudeIsOptional() throws Exception {
        call("10.6", "106.7", "300", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.elevation.terrainElevationMeters").value(18.2))
                .andExpect(jsonPath("$.data.elevation.estimatedFlightAltitudeAmslMeters").doesNotExist());
    }

    @Test
    void rejectsAnInvalidLatitude() throws Exception {
        call("91", "106.7", "300", "60").andExpect(status().isBadRequest());
        call("-90.5", "106.7", "300", "60").andExpect(status().isBadRequest());
        call("north", "106.7", "300", "60").andExpect(status().isBadRequest());
        call("NaN", "106.7", "300", "60").andExpect(status().isBadRequest());
        verify(zones, never()).assess(anyDouble(), anyDouble(), anyDouble());
    }

    @Test
    void rejectsAnInvalidLongitude() throws Exception {
        call("10.6", "181", "300", "60").andExpect(status().isBadRequest());
        call("10.6", "-181", "300", "60").andExpect(status().isBadRequest());
        call("10.6", "", "300", "60").andExpect(status().isBadRequest());
    }

    @Test
    void rejectsAnInvalidRadius() throws Exception {
        call("10.6", "106.7", "0", "60").andExpect(status().isBadRequest());
        call("10.6", "106.7", "-5", "60").andExpect(status().isBadRequest());
        call("10.6", "106.7", "5000.5", "60").andExpect(status().isBadRequest());
        call("10.6", "106.7", "wide", "60").andExpect(status().isBadRequest());
    }

    @Test
    void rejectsAnInvalidAltitude() throws Exception {
        call("10.6", "106.7", "300", "0").andExpect(status().isBadRequest());
        call("10.6", "106.7", "300", "-60").andExpect(status().isBadRequest());
        call("10.6", "106.7", "300", "9999").andExpect(status().isBadRequest());
    }

    @Test
    void aMissingRequiredParameterIsABadRequest() throws Exception {
        mvc.perform(get("/api/flight-area-assessment").param("latitude", "10.6").param("longitude", "106.7"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void anElevationOutageStillReturnsTheAssessment() throws Exception {
        when(elevation.lookup(anyDouble(), anyDouble()))
                .thenReturn(new ElevationResult(false, null, "AMSL", "Open-Meteo", "PROVIDER_UNAVAILABLE"));

        call("10.6", "106.7", "300", "60")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.elevation.available").value(false))
                .andExpect(jsonPath("$.data.elevation.estimatedFlightAltitudeAmslMeters").doesNotExist())
                .andExpect(jsonPath("$.data.osmContext.available").value(true))
                .andExpect(jsonPath("$.data.assessment.level").value("NEEDS_REVIEW"))
                .andExpect(jsonPath("$.data.assessment.findings[0].code").value("ELEVATION_UNAVAILABLE"));
    }

    @Test
    void whenBothProvidersAreDownTheZoneAssessmentStillRuns() throws Exception {
        when(elevation.lookup(anyDouble(), anyDouble()))
                .thenReturn(new ElevationResult(false, null, "AMSL", "Open-Meteo", "PROVIDER_UNAVAILABLE"));
        when(osm.assess(anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(new OsmContext(false, "PROVIDER_UNAVAILABLE", null, 0, 0, 0, 0, false, false, List.of()));
        when(zones.assess(anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(new RestrictedZones(true, true, true, 0.0, List.of(
                        new FlightAreaAssessmentResponse.AffectedZone("z1", "HCMC_TSN_NO_FLY", "TSN", "RESTRICTED",
                                null, true, 0.0, true))));

        call("10.8", "106.65", "300", "60")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.restrictedZones.pointInsideRestrictedZone").value(true))
                .andExpect(jsonPath("$.data.osmContext.available").value(false))
                .andExpect(jsonPath("$.data.osmContext.errorCode").value("PROVIDER_UNAVAILABLE"))
                .andExpect(jsonPath("$.data.assessment.level").value("HIGH_RISK"));
    }

    @Test
    void aSlowProviderIsCutOffAndReportedAsATimeout() throws Exception {
        FlightAreaProperties properties = new FlightAreaProperties();
        properties.setAggregateTimeoutMs(100);
        var slowService = new FlightAreaAssessmentService(elevation, zones, osm, new FlightAreaRiskEvaluator(properties),
                properties, Executors.newFixedThreadPool(2));
        when(osm.assess(anyDouble(), anyDouble(), anyDouble())).thenAnswer(invocation -> {
            Thread.sleep(2000);
            return new OsmContext(true, null, 300.0, 1, 0, 0, 0, false, false, List.of());
        });

        var response = slowService.assess("10.6", "106.7", "300", "60");

        assertFalse(response.osmContext().available());
        assertEquals("PROVIDER_TIMEOUT", response.osmContext().errorCode());
        assertTrue(response.elevation().available());
    }

    @Test
    void theEndpointIsRestrictedToCustomersAndStaff() throws Exception {
        var method = FlightAreaAssessmentController.class.getMethod(
                "assess", String.class, String.class, String.class, String.class);
        assertEquals("hasAnyRole('CUSTOMER','STAFF')", method.getAnnotation(PreAuthorize.class).value());
    }
}
