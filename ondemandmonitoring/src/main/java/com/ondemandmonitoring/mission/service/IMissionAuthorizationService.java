package com.ondemandmonitoring.mission.service;

public interface IMissionAuthorizationService {

    boolean isAssignedStaff(String missionId);

    boolean isAssignedStaffForPreflight(String preflightId);
}
