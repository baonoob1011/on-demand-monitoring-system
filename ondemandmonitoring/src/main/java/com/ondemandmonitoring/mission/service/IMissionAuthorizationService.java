package com.ondemandmonitoring.mission.service;

public interface IMissionAuthorizationService {
    boolean canManageMissions();
    boolean canViewStaffMissions(String staffId);
    boolean canViewMission(String identifier);
    boolean canRespondToMission(String identifier);
    boolean canRespondToMission(String identifier, String staffId);
    boolean canControlFlight(String identifier);
    boolean canOperatePayload(String identifier);
    boolean canExecuteMonitoringChecklist(String identifier);
    boolean canInspectDevice(String identifier);
    boolean canMaintainDevice(String identifier);
    boolean canViewMissionMedia(String identifier);
    boolean canUploadMissionMedia(String identifier);
    boolean canCompleteMission(String identifier);
    boolean canSubmitMissionResult(String identifier);
}
