package com.ondemandmonitoring.device.service;

import com.ondemandmonitoring.device.dto.request.TelemetryRequest;
import com.ondemandmonitoring.device.domain.DeviceTelemetry;

public interface IDeviceTelemetryService {
    DeviceTelemetry save(String deviceCode, TelemetryRequest request);
    DeviceTelemetry saveForRegisteredDevice(String deviceCode, TelemetryRequest request);

    default DeviceTelemetry saveForRegisteredDrone(String droneCode, TelemetryRequest request) {
        return saveForRegisteredDevice(droneCode, request);
    }
}
