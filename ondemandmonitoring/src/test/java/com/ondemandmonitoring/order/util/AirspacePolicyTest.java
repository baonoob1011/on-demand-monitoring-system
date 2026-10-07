package com.ondemandmonitoring.order.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;

class AirspacePolicyTest {

    private static final GeometryFactory FACTORY = new GeometryFactory();

    private static Point point(double lon, double lat) {
        return FACTORY.createPoint(new org.locationtech.jts.geom.Coordinate(lon, lat));
    }

    @Test
    void pointNextToTanSonNhatNeedsPermit() {
        var zone = AirspacePolicy.findPermitZone(point(106.6600, 10.8150), null);
        assertTrue(zone.isPresent());
        assertEquals("TAN_SON_NHAT", zone.get().code());
    }

    @Test
    void districtOneIsOutsideTheZone() {
        assertTrue(AirspacePolicy.findPermitZone(point(106.7009, 10.7769), null).isEmpty());
    }

    @Test
    void farAwayPointDoesNotNeedPermit() {
        assertTrue(AirspacePolicy.findPermitZone(point(106.9500, 10.4000), null).isEmpty());
    }
}
