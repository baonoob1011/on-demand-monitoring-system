package com.ondemandmonitoring.zone.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.ondemandmonitoring.zone.domain.Zone;
import com.ondemandmonitoring.zone.dto.request.ZoneCreateRequest;
import com.ondemandmonitoring.zone.dto.request.ZonePolygonUpdateRequest;
import com.ondemandmonitoring.zone.dto.response.ZoneResponse;
import com.ondemandmonitoring.zone.mapper.ZoneMapper;
import com.ondemandmonitoring.zone.repository.ZoneRepository;
import com.ondemandmonitoring.zone.service.impl.ZoneService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Polygon;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ZoneServiceTest {

    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory();

    @Mock
    ZoneRepository zoneRepository;

    @Mock
    ZoneMapper zoneMapper;

    ZoneService zoneService;

    @BeforeEach
    void setUp() {
        zoneService = new ZoneService(zoneRepository, zoneMapper);
        when(zoneRepository.save(any(Zone.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(zoneMapper.toResponse(any(Zone.class))).thenAnswer(invocation -> {
            Zone zone = invocation.getArgument(0);
            return ZoneResponse.builder()
                    .id(zone.getId())
                    .code(zone.getCode())
                    .name(zone.getName())
                    .zoneType(zone.getZoneType())
                    .restricted(zone.isRestricted())
                    .coordinates(List.of())
                    .build();
        });
    }

    @Test
    void createDefaultsRestrictedToFalseWhenOmitted() {
        ZoneCreateRequest request = createRequest(null);

        ZoneResponse response = zoneService.create(request);

        assertThat(response.getRestricted()).isFalse();
    }

    @Test
    void createPersistsRestrictedTrueWhenRequested() {
        ZoneCreateRequest request = createRequest(true);

        ZoneResponse response = zoneService.create(request);

        assertThat(response.getRestricted()).isTrue();
    }

    @Test
    void updatePolygonCanChangeRestrictedFalseToTrue() {
        Zone zone = existingZone(false);
        when(zoneRepository.findById("zone-1")).thenReturn(Optional.of(zone));
        ZonePolygonUpdateRequest request = polygonUpdateRequest(true);

        ZoneResponse response = zoneService.updatePolygon("zone-1", request);

        assertThat(response.getRestricted()).isTrue();
        assertThat(zone.getPolygon()).isNotNull();
    }

    @Test
    void updatePolygonCanChangeRestrictedTrueToFalse() {
        Zone zone = existingZone(true);
        when(zoneRepository.findById("zone-1")).thenReturn(Optional.of(zone));
        ZonePolygonUpdateRequest request = polygonUpdateRequest(false);

        ZoneResponse response = zoneService.updatePolygon("zone-1", request);

        assertThat(response.getRestricted()).isFalse();
        assertThat(zone.getPolygon()).isNotNull();
    }

    private ZoneCreateRequest createRequest(Boolean restricted) {
        ZoneCreateRequest request = new ZoneCreateRequest();
        request.setName("Restricted Test Area");
        request.setZoneType("CUSTOM");
        request.setRestricted(restricted);
        request.setCoordinates(square(0, 0, 10, 10));
        return request;
    }

    private ZonePolygonUpdateRequest polygonUpdateRequest(Boolean restricted) {
        ZonePolygonUpdateRequest request = new ZonePolygonUpdateRequest();
        request.setRestricted(restricted);
        request.setCoordinates(square(2, 2, 12, 12));
        return request;
    }

    private Zone existingZone(boolean restricted) {
        Zone zone = new Zone();
        zone.setCode("TEST_ZONE");
        zone.setName("Test Zone");
        zone.setZoneType("CUSTOM");
        zone.setPurpose("Test");
        zone.setRestricted(restricted);
        zone.setCenterXM(5.0);
        zone.setCenterYM(5.0);
        zone.setRadiusM(8.0);
        zone.setSourceWorld("test");
        zone.setCoordinateSystem("LOCAL_SIMULATION_METERS_GAZEBO_XY");
        zone.setPolygon(mockPolygon());
        return zone;
    }

    private Polygon mockPolygon() {
        Coordinate[] ring = {
                new Coordinate(0, 0),
                new Coordinate(10, 0),
                new Coordinate(10, 10),
                new Coordinate(0, 10),
                new Coordinate(0, 0)
        };
        return GEOMETRY_FACTORY.createPolygon(GEOMETRY_FACTORY.createLinearRing(ring));
    }

    private List<List<Double>> square(double minX, double minY, double maxX, double maxY) {
        return List.of(
                List.of(minX, minY),
                List.of(maxX, minY),
                List.of(maxX, maxY),
                List.of(minX, maxY),
                List.of(minX, minY));
    }
}
