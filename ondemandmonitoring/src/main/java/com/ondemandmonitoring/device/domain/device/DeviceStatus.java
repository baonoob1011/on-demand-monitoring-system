package com.ondemandmonitoring.device.domain.device;

/** Lifecycle state owned by the Device module. */
public enum DeviceStatus {
    OFFLINE,
    AVAILABLE,
    RESERVED,
    PREPARING,
    IN_FLIGHT,
    MONITORING,
    RETURNING,
    POST_FLIGHT_CHECK,
    MAINTENANCE,
    EMERGENCY
}
