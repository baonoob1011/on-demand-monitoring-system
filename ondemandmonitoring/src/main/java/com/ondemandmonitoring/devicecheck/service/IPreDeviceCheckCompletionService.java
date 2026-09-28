package com.ondemandmonitoring.devicecheck.service;

import com.ondemandmonitoring.devicecheck.dto.response.PreDeviceCheckResponse;

public interface IPreDeviceCheckCompletionService {
    PreDeviceCheckResponse complete(String missionId, String deviceId);
}
