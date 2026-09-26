package com.ondemandmonitoring.mission.service;

import com.ondemandmonitoring.mission.dto.response.MissionMediaContext;
import java.util.List;

/** Public mission boundary for media use cases. Authorization stays with mission ownership. */
public interface IMissionMediaAccessService {
    MissionMediaContext authorizeOperator(String missionIdentifier);
    void requireAssignedDrone(String missionId, String droneId);
    String authorizeCustomer(String missionIdentifier);
    List<String> ownCustomerMissionIds();
}
