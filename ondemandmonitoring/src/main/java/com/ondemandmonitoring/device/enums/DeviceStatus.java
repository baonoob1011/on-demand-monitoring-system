package com.ondemandmonitoring.device.enums;

public enum DeviceStatus {
    AVAILABLE,
    RESERVED,
    PREFLIGHT,
    /** Drone is actively flying on a mission (replaces IN_MISSION in diagram). */
    IN_MISSION,
    /** Diagram label: ACTIVE_MISSION – drone airborne, transmitting realtime data. */
    ACTIVE_MISSION,
    RETURNING,
    CHARGING,
    /** Diagram label: IDLE_CHARGING – drone sent to charge station after battery-low preflight failure. */
    IDLE_CHARGING,
    MAINTENANCE,
    OUT_OF_SERVICE,
    OFFLINE
}
