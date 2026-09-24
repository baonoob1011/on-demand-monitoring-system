package com.ondemandmonitoring.drone.service;

import com.ondemandmonitoring.drone.dto.request.TelemetryRequest;
import com.ondemandmonitoring.drone.domain.DroneTelemetry;

public interface IDroneTelemetryService {
    DroneTelemetry save(String droneCode, TelemetryRequest request);
    DroneTelemetry saveForRegisteredDrone(String droneCode, TelemetryRequest request);
}
