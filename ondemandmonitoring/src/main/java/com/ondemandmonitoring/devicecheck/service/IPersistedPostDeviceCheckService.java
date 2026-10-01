package com.ondemandmonitoring.devicecheck.service;

import com.ondemandmonitoring.devicecheck.dto.request.DeviceCheckItemUpdateRequest;
import com.ondemandmonitoring.devicecheck.dto.response.PersistedPostDeviceCheckResponse;
import java.util.List;

public interface IPersistedPostDeviceCheckService {
    PersistedPostDeviceCheckResponse start(String missionId);
    List<PersistedPostDeviceCheckResponse> history(String missionId);
    PersistedPostDeviceCheckResponse current(String missionId);
    PersistedPostDeviceCheckResponse get(String id);
    PersistedPostDeviceCheckResponse update(String id, String type, DeviceCheckItemUpdateRequest request);
}


