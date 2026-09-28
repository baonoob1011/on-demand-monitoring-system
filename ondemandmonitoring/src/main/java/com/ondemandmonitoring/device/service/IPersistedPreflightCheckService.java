package com.ondemandmonitoring.device.service;

import com.ondemandmonitoring.device.dto.request.PreflightItemUpdateRequest;
import com.ondemandmonitoring.device.dto.response.PersistedPreDeviceCheckResponse;
import java.util.List;

public interface IPersistedPreflightCheckService {
    PersistedPreDeviceCheckResponse start(String missionId);
    List<PersistedPreDeviceCheckResponse> history(String missionId);
    PersistedPreDeviceCheckResponse current(String missionId);
    PersistedPreDeviceCheckResponse get(String id);
    PersistedPreDeviceCheckResponse update(String id, String type, PreflightItemUpdateRequest request);
}

