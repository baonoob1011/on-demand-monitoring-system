package com.ondemandmonitoring.device.service;

import com.ondemandmonitoring.device.dto.request.TelemetryRequest;
import com.ondemandmonitoring.device.domain.DroneTelemetry;

public interface IDroneTelemetryService {
    DroneTelemetry save(String droneCode, TelemetryRequest request);
    DroneTelemetry saveForRegisteredDrone(String droneCode, TelemetryRequest request);
}
