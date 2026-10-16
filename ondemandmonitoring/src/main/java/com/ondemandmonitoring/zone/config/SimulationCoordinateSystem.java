package com.ondemandmonitoring.zone.config;

/** Coordinate/source constants used by simulation services. This class does not seed data. */
public final class SimulationCoordinateSystem {
    public static final String SOURCE_WORLD = "forest_monitoring_compact";
    public static final String COORDINATE_SYSTEM = "LOCAL_SIMULATION_METERS_GAZEBO_XY";
    public static final String GPS_COORDINATE_SYSTEM = "WGS84_GPS_LON_LAT";

    private SimulationCoordinateSystem() {
    }
}
