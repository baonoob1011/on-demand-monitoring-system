package com.ondemandmonitoring.device.domain.inspection;

/** Pre-flight inspection record for a device assigned to a mission. */
public record PreFlightInspection(String id, String deviceCode, boolean passed) {
}
