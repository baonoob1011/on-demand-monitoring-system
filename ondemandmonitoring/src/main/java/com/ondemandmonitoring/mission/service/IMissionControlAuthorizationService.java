package com.ondemandmonitoring.mission.service;

import com.ondemandmonitoring.mission.dto.response.MissionControlContextResponse;

public interface IMissionControlAuthorizationService {
    MissionControlContextResponse authorize(String missionId, String droneId);
}
