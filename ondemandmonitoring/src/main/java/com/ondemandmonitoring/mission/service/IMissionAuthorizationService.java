package com.ondemandmonitoring.mission.service;

public interface IMissionAuthorizationService {

    boolean isAssignedStaff(String missionId);

    boolean isAssignedOperator(String missionId);

    boolean isAssignedStaffForPreDeviceCheck(String preDeviceCheckId);
}
