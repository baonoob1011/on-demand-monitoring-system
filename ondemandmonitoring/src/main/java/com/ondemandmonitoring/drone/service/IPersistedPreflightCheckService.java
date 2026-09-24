package com.ondemandmonitoring.drone.service;

import com.ondemandmonitoring.drone.dto.request.PreflightItemUpdateRequest;
import com.ondemandmonitoring.drone.dto.response.PersistedPreflightCheckResponse;
import java.util.List;

public interface IPersistedPreflightCheckService {
    PersistedPreflightCheckResponse start(String missionId);
    List<PersistedPreflightCheckResponse> history(String missionId);
    PersistedPreflightCheckResponse current(String missionId);
    PersistedPreflightCheckResponse get(String id);
    PersistedPreflightCheckResponse update(String id, String type, PreflightItemUpdateRequest request);
}
