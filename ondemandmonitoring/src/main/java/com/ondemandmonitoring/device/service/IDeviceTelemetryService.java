package com.ondemandmonitoring.device.service;

import com.ondemandmonitoring.device.dto.request.TelemetryRequest;

public interface IDeviceTelemetryService {

    void record(String deviceId, TelemetryRequest request);
}
