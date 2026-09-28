package com.ondemandmonitoring.devicecheck.service;

import com.ondemandmonitoring.devicecheck.dto.request.PreDeviceCheckItemUpdateRequest;
import com.ondemandmonitoring.devicecheck.dto.response.PersistedPreDeviceCheckResponse;
import java.util.List;

public interface IPersistedPreDeviceCheckService {
    PersistedPreDeviceCheckResponse start(String missionId);
    List<PersistedPreDeviceCheckResponse> history(String missionId);
    PersistedPreDeviceCheckResponse current(String missionId);
    PersistedPreDeviceCheckResponse get(String id);
    PersistedPreDeviceCheckResponse update(String id, String type, PreDeviceCheckItemUpdateRequest request);
}

