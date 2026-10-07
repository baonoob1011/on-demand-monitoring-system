package com.ondemandmonitoring.flightarea.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.ondemandmonitoring.flightarea.client.OverpassClient;
import com.ondemandmonitoring.flightarea.config.FlightAreaProperties;
import com.ondemandmonitoring.flightarea.dto.FlightAreaAssessmentResponse.NearbyFeature;
import com.ondemandmonitoring.flightarea.dto.FlightAreaAssessmentResponse.OsmContext;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

/** Uses a mocked HTTP server: the real Overpass API is never called. */
class OsmAreaAssessmentServiceTest {

    private static final double LAT = 10.6398;
    private static final double LON = 106.7173;

    private MockRestServiceServer server;
    private OsmAreaAssessmentService service;
    private FlightAreaProperties properties;

    @BeforeEach
    void setUp() {
        properties = new FlightAreaProperties();
        properties.getOsm().setOverpassUrl("http://overpass.test/api/interpreter");
        properties.getOsm().setRetryDelayMs(0);
        RestTemplate restTemplate = new RestTemplate();
        server = MockRestServiceServer.bindTo(restTemplate).build();
        Clock clock = Clock.fixed(Instant.parse("2026-10-07T03:00:00Z"), ZoneOffset.UTC);
        service = new OsmAreaAssessmentService(new OverpassClient(restTemplate, properties), properties, clock);
    }

    private void respond(String json) {
        server.expect(requestTo("http://overpass.test/api/interpreter"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));
    }

    private static String count(int buildings) {
        return "{\"type\":\"count\",\"id\":0,\"tags\":{\"nodes\":\"0\",\"ways\":\"" + buildings
                + "\",\"relations\":\"0\",\"total\":\"" + buildings + "\"}}";
    }

    private static String body(String... elements) {
        return "{\"elements\":[" + String.join(",", elements) + "]}";
    }

    @Test
    void countsBuildingsAndClassifiesTowersMastsAndPowerTowers() {
        respond(body(
                count(34),
                "{\"type\":\"node\",\"id\":1,\"lat\":10.6401,\"lon\":106.7173,\"tags\":{\"man_made\":\"tower\",\"name\":\"Tháp A\",\"height\":\"45\"}}",
                "{\"type\":\"node\",\"id\":2,\"lat\":10.6402,\"lon\":106.7174,\"tags\":{\"man_made\":\"mast\"}}",
                "{\"type\":\"node\",\"id\":3,\"lat\":10.6403,\"lon\":106.7175,\"tags\":{\"power\":\"tower\"}}",
                "{\"type\":\"node\",\"id\":4,\"lat\":10.6404,\"lon\":106.7176,\"tags\":{\"power\":\"tower\"}}"));

        OsmContext context = service.assess(LAT, LON, 300);

        assertTrue(context.available());
        assertEquals(34, context.buildingCount());
        assertEquals(1, context.towerCount());
        assertEquals(1, context.mastCount());
        assertEquals(2, context.powerTowerCount());
        assertFalse(context.aerodromeNearby());
        assertFalse(context.helipadNearby());
        assertEquals(4, context.importantFeatures().size());
        assertTrue(context.importantFeatures().stream().allMatch(f -> "OPENSTREETMAP".equals(f.source())));
        server.verify();
    }

    @Test
    void detectsAnAerodromeAndAHelipadFromWaysUsingTheirCentre() {
        respond(body(
                count(0),
                "{\"type\":\"way\",\"id\":10,\"center\":{\"lat\":10.65,\"lon\":106.72},\"tags\":{\"aeroway\":\"aerodrome\",\"name\":\"Sân bay X\"}}",
                "{\"type\":\"node\",\"id\":11,\"lat\":10.641,\"lon\":106.718,\"tags\":{\"aeroway\":\"helipad\"}}"));

        OsmContext context = service.assess(LAT, LON, 300);

        assertTrue(context.aerodromeNearby());
        assertTrue(context.helipadNearby());
        NearbyFeature first = context.importantFeatures().get(0);
        // Aerodromes and helipads are listed before obstacles, nearest first.
        assertEquals("HELIPAD", first.type());
        assertEquals("AERODROME", context.importantFeatures().get(1).type());
        assertEquals("Sân bay X", context.importantFeatures().get(1).name());
    }

    @Test
    void neverInventsAHeight() {
        respond(body(
                count(3),
                "{\"type\":\"node\",\"id\":1,\"lat\":10.6401,\"lon\":106.7173,\"tags\":{\"man_made\":\"tower\"}}",
                "{\"type\":\"node\",\"id\":2,\"lat\":10.6402,\"lon\":106.7173,\"tags\":{\"man_made\":\"tower\",\"height\":\"82'\"}}",
                "{\"type\":\"node\",\"id\":3,\"lat\":10.6403,\"lon\":106.7173,\"tags\":{\"man_made\":\"tower\",\"height\":\"30 m\"}}",
                "{\"type\":\"way\",\"id\":4,\"center\":{\"lat\":10.6404,\"lon\":106.7173},\"tags\":{\"building\":\"yes\",\"building:levels\":\"12\"}}"));

        OsmContext context = service.assess(LAT, LON, 300);

        assertEquals(3, context.towerCount());
        // "building:levels" alone is not a height and the building is only counted.
        assertEquals(3, context.importantFeatures().size());
        assertNull(context.importantFeatures().get(0).heightMeters());
        assertNull(context.importantFeatures().get(1).heightMeters(), "feet are not parsed");
        assertEquals(30.0, context.importantFeatures().get(2).heightMeters());
    }

    @Test
    void keepsOnlyTheMostRelevantFeatures() {
        String towers = IntStream.range(0, 40)
                .mapToObj(i -> "{\"type\":\"node\",\"id\":" + (100 + i) + ",\"lat\":" + (10.6398 + i * 0.0001)
                        + ",\"lon\":106.7173,\"tags\":{\"power\":\"tower\"}}")
                .collect(Collectors.joining(","));
        respond("{\"elements\":[" + count(500) + "," + towers + "]}");

        OsmContext context = service.assess(LAT, LON, 300);

        assertEquals(40, context.powerTowerCount());
        assertEquals(15, context.importantFeatures().size());
        for (int i = 1; i < context.importantFeatures().size(); i++) {
            assertTrue(context.importantFeatures().get(i - 1).distanceMeters()
                    <= context.importantFeatures().get(i).distanceMeters());
        }
    }

    @Test
    void queriesWithACappedRadiusAndGeneratesTheQueryItself() {
        properties.getOsm().setQueryMaxRadiusM(1000);
        server.expect(requestTo("http://overpass.test/api/interpreter"))
                .andExpect(content().string(Matchers.containsString("around%3A1000%2C10.639800%2C106.717300")))
                .andRespond(withSuccess(body(count(1)), MediaType.APPLICATION_JSON));

        OsmContext context = service.assess(LAT, LON, 4000);

        assertEquals(1000.0, context.queryRadiusMeters());
        server.verify();
    }

    @Test
    void aProviderFailureMakesTheContextUnavailableAfterTheConfiguredAttempts() {
        server.expect(ExpectedCount.times(2), requestTo("http://overpass.test/api/interpreter"))
                .andRespond(withServerError());

        OsmContext context = service.assess(LAT, LON, 300);

        assertFalse(context.available());
        assertEquals("PROVIDER_UNAVAILABLE", context.errorCode());
        assertTrue(context.importantFeatures().isEmpty());
        server.verify();
    }

    @Test
    void aBusyServerIsRetriedAndTheSecondAnswerIsUsed() {
        server.expect(requestTo("http://overpass.test/api/interpreter"))
                .andRespond(withStatus(HttpStatus.GATEWAY_TIMEOUT).body("<html>server is probably too busy</html>"));
        respond(body(count(7)));

        OsmContext context = service.assess(LAT, LON, 300);

        assertTrue(context.available());
        assertEquals(7, context.buildingCount());
        server.verify();
    }

    @Test
    void anUnusableAnswerIsNotRetried() {
        respond("<html>not json</html>");

        OsmContext context = service.assess(LAT, LON, 300);

        assertFalse(context.available());
        assertEquals("PROVIDER_BAD_RESPONSE", context.errorCode());
        server.verify();
    }

    @Test
    void anUnusableResponseMakesTheContextUnavailable() {
        for (String json : new String[] {"<html>busy</html>", "{}", "{\"elements\":[]}"}) {
            server.reset();
            respond(json);
            OsmContext context = service.assess(LAT + json.length() * 0.001, LON, 300);
            assertFalse(context.available(), json);
            assertEquals("PROVIDER_BAD_RESPONSE", context.errorCode(), json);
        }
    }

    @Test
    void identicalAreasAreServedFromTheCache() {
        respond(body(count(5)));

        service.assess(LAT, LON, 300);
        OsmContext again = service.assess(LAT + 0.00001, LON, 300);

        assertTrue(again.available());
        assertEquals(5, again.buildingCount());
        server.verify();
    }
}
