package com.ondemandmonitoring.mapillary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class MapillaryImageServiceTest {
    private final MapillaryProperties properties = new MapillaryProperties();
    private final MapillaryClient client = mock(MapillaryClient.class);
    private final MapillaryImageService service = new MapillaryImageService(client, properties);

    @Test
    void parsesMapillaryPayload() {
        MapillaryClient real = new MapillaryClient(properties);
        String body = """
                {"data":[{"id":"123","geometry":{"type":"Point","coordinates":[106.70,10.77]},
                "captured_at":1700000000000,"compass_angle":90.5,"is_pano":false,
                "thumb_2048_url":"https://cdn.test/a.jpg"},
                {"id":"bad","geometry":{"coordinates":[1,2]}}]}
                """;
        List<MapillaryImageCandidate> parsed = real.parse(body);
        assertThat(parsed).hasSize(1);
        assertThat(parsed.get(0).imageId()).isEqualTo("123");
        assertThat(parsed.get(0).latitude()).isEqualTo(10.77);
        assertThat(parsed.get(0).longitude()).isEqualTo(106.70);
        assertThat(parsed.get(0).thumbUrl()).isEqualTo("https://cdn.test/a.jpg");
    }

    @Test
    void picksNearestAndIgnoresImagesFarOutsideRadius() {
        var near = new MapillaryImageCandidate("near", 10.7701, 106.7000, Instant.now(), null, false, "u1");
        var far = new MapillaryImageCandidate("far", 10.7800, 106.7000, Instant.now(), null, false, "u2");
        when(client.searchNearby(anyDouble(), anyDouble(), anyDouble(), anyInt())).thenReturn(List.of(far, near));
        assertThat(service.findNearestImage(10.7700, 106.7000)).get().extracting(MapillaryImageCandidate::imageId)
                .isEqualTo("near");
    }

    @Test
    void returnsEmptyWhenNothingNearby() {
        when(client.searchNearby(anyDouble(), anyDouble(), anyDouble(), anyInt())).thenReturn(List.of());
        assertThat(service.findNearestImage(10.77, 106.70)).isEmpty();
    }

    @Test
    void headingMatchBreaksTies() {
        var facing = new MapillaryImageCandidate("facing", 10.77010, 106.7000, Instant.now(), 90.0, false, "u1");
        var away = new MapillaryImageCandidate("away", 10.77010, 106.7000, Instant.now(), 270.0, false, "u2");
        when(client.searchNearby(anyDouble(), anyDouble(), anyDouble(), anyInt())).thenReturn(List.of(away, facing));
        assertThat(service.findNearestImage(10.77, 106.70, 90.0)).get()
                .extracting(MapillaryImageCandidate::imageId).isEqualTo("facing");
    }
}
