package com.ondemandmonitoring.device.service;

import com.ondemandmonitoring.device.dto.request.DeviceCheckItemUpdateRequest;
import com.ondemandmonitoring.device.dto.response.PersistedPostDeviceCheckResponse;
import java.util.List;

public interface IPersistedPostDeviceCheckService {
    PersistedPostDeviceCheckResponse start(String missionId);
    List<PersistedPostDeviceCheckResponse> history(String missionId);
    PersistedPostDeviceCheckResponse current(String missionId);
    PersistedPostDeviceCheckResponse get(String id);
    PersistedPostDeviceCheckResponse update(String id, String type, DeviceCheckItemUpdateRequest request);
}


