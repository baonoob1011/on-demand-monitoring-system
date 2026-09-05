package com.ondemandmonitoring.device.domain.maintenance;

/** Maintenance work item raised when a device fails inspection. */
public record MaintenanceTicket(String id, String deviceCode, String reason) {
}
