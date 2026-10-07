package com.ondemandmonitoring.mission.service;

import com.ondemandmonitoring.mission.dto.request.*;
import com.ondemandmonitoring.mission.dto.response.MissionSafeStopResponse;
public interface IMissionSafeStopService {
    MissionSafeStopResponse request(String missionId, MissionSafeStopRequest request);
    MissionSafeStopResponse confirm(String missionId, MissionSafeStopConfirmationRequest request);
    MissionSafeStopResponse cleanup(String missionId);
    MissionSafeStopResponse fail(String missionId, String reason);
    MissionSafeStopResponse get(String missionId);
}
