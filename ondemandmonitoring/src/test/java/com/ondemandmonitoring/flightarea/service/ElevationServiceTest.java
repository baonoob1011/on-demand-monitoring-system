package com.ondemandmonitoring.flightarea.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.ondemandmonitoring.flightarea.client.ElevationClient;
import com.ondemandmonitoring.flightarea.config.FlightAreaProperties;
import com.ondemandmonitoring.flightarea.service.ElevationService.ElevationResult;
import java.net.SocketTimeoutException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

/** Uses a mocked HTTP server: the real elevation provider is never called. */
class ElevationServiceTest {

    private MockRestServiceServer server;
    private ElevationService service;

    @BeforeEach
    void setUp() {
        FlightAreaProperties properties = new FlightAreaProperties();
        properties.getElevation().setBaseUrl("http://elevation.test");
        RestTemplate restTemplate = new RestTemplate();
        server = MockRestServiceServer.bindTo(restTemplate).build();
        Clock clock = Clock.fixed(Instant.parse("2026-10-07T03:00:00Z"), ZoneOffset.UTC);
        service = new ElevationService(new ElevationClient(restTemplate, properties), properties, clock);
    }

    @Test
    void readsTheTerrainElevationAsAmsl() {
        server.expect(requestTo(org.hamcrest.Matchers.startsWith("http://elevation.test/v1/elevation")))
                .andExpect(method(HttpMethod.GET))
                .andExpect(queryParam("latitude", "10.639800"))
                .andExpect(queryParam("longitude", "106.717300"))
                .andRespond(withSuccess("{\"elevation\":[18.2]}", MediaType.APPLICATION_JSON));

        ElevationResult result = service.lookup(10.6398245, 106.7172615);

        assertTrue(result.available());
        assertEquals(18.2, result.elevationMeters());
        assertEquals("AMSL", result.reference());
        assertNull(result.errorCode());
        server.verify();
    }

    @Test
    void repeatedLookupsForTheSamePlaceHitTheProviderOnce() {
        server.expect(ExpectedCount.once(), requestTo(org.hamcrest.Matchers.startsWith("http://elevation.test")))
                .andRespond(withSuccess("{\"elevation\":[7.0]}", MediaType.APPLICATION_JSON));

        service.lookup(10.63982, 106.71726);
        ElevationResult second = service.lookup(10.63984, 106.71728);

        assertTrue(second.available());
        assertEquals(7.0, second.elevationMeters());
        server.verify();
    }

    @Test
    void aTimeoutMakesTheResultUnavailableInsteadOfThrowing() {
        server.expect(requestTo(org.hamcrest.Matchers.startsWith("http://elevation.test")))
                .andRespond(withException(new SocketTimeoutException("read timed out")));

        ElevationResult result = service.lookup(10.6, 106.7);

        assertFalse(result.available());
        assertNull(result.elevationMeters());
        assertEquals("PROVIDER_UNAVAILABLE", result.errorCode());
    }

    @Test
    void aServerErrorMakesTheResultUnavailable() {
        server.expect(requestTo(org.hamcrest.Matchers.startsWith("http://elevation.test")))
                .andRespond(withServerError());

        ElevationResult result = service.lookup(10.6, 106.7);

        assertFalse(result.available());
        assertEquals("PROVIDER_UNAVAILABLE", result.errorCode());
    }

    @Test
    void malformedBodiesAreRejectedSafely() {
        for (String body : new String[] {"not json", "{}", "{\"elevation\":[]}", "{\"elevation\":[\"high\"]}"}) {
            server.reset();
            server.expect(requestTo(org.hamcrest.Matchers.startsWith("http://elevation.test")))
                    .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

            ElevationResult result = service.lookup(11.0 + body.length() / 100.0, 107.0);

            assertFalse(result.available(), body);
            assertEquals("PROVIDER_BAD_RESPONSE", result.errorCode(), body);
        }
    }

    @Test
    void failuresAreNotCached() {
        server.expect(requestTo(org.hamcrest.Matchers.startsWith("http://elevation.test")))
                .andRespond(withServerError());
        assertFalse(service.lookup(10.6, 106.7).available());

        server.reset();
        server.expect(requestTo(org.hamcrest.Matchers.startsWith("http://elevation.test")))
                .andRespond(withSuccess("{\"elevation\":[3.0]}", MediaType.APPLICATION_JSON));
        assertTrue(service.lookup(10.6, 106.7).available());
    }
}
