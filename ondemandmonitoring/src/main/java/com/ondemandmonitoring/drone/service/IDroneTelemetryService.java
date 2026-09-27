package com.ondemandmonitoring.drone.service;

import com.ondemandmonitoring.drone.dto.request.TelemetryRequest;
import com.ondemandmonitoring.drone.domain.DeviceTelemetry;

public interface IDroneTelemetryService {
    DeviceTelemetry save(String droneCode, TelemetryRequest request);

    DeviceTelemetry saveForRegisteredDrone(String droneCode, TelemetryRequest request);
}
