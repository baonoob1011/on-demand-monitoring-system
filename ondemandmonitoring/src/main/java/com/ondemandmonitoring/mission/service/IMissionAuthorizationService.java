package com.ondemandmonitoring.mission.service;

public interface IMissionAuthorizationService {
    boolean canViewMissionByCode(String missionCode);
    boolean canManageMissions();
    boolean canViewStaffMissions(String staffId);
    boolean canViewMission(String missionId);
    boolean canRespondToMission(String missionId);
    boolean canRespondToMission(String missionId, String staffId);
    boolean canControlFlight(String missionId);
    boolean canTerminateFlight(String missionId);
    boolean canPrepareSafeStopPostflight(String missionId);
    boolean canOperatePayload(String missionId);
    boolean canExecuteMonitoringChecklist(String missionId);
    boolean canInspectDevice(String missionId);
    boolean canMaintainDevice(String missionId);
    boolean canViewMissionMedia(String missionId);
    boolean canUploadMissionMedia(String missionId);
    boolean canCompleteMission(String missionId);
    boolean canSubmitMissionResult(String missionId);
    boolean canAttachChecklistEvidence(String missionId);
    boolean canDetachChecklistEvidence(String missionId);
}
