package com.ondemandmonitoring.device.domain.device;

/** Drone/device aggregate root. */
public record Device(String code, DeviceStatus status) {
}
