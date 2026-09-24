package com.ondemandmonitoring.mission.service;

public interface IMissionAuthorizationService {

    boolean isAssignedOperator(String missionId);

    boolean isAssignedOperatorForPreflight(String preflightId);
}
