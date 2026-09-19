package com.ondemandmonitoring.mission.service;

import com.ondemandmonitoring.drone.dto.response.PreflightCheckResponse;
import com.ondemandmonitoring.drone.enums.DroneStatus;
import com.ondemandmonitoring.mission.domain.Mission;
import com.ondemandmonitoring.mission.dto.response.MissionResponse;

/**
 * Application service interface for mission lifecycle and assignment orchestration (Flow 3).
 * Returns DTOs (MissionResponse) to decouple domain entities from presentation/controller layers.
 */
public interface IMissionService {

    MissionResponse getByIdResponse(String missionId);

    MissionResponse getByCodeResponse(String missionCode);
    MissionResponse createMissionForOrder(String orderId);
    MissionResponse assignDrone(String missionId, String droneId);
    MissionResponse assignOperator(String missionId, String operatorId);
    Mission findById(String missionId);
    MissionResponse acceptMission(String missionId, String operatorId);
    MissionResponse rejectMission(String missionId, String operatorId, String reason);
    MissionResponse connectGcs(String missionId);
    PreflightCheckResponse runPreflightCheck(String missionId, String droneCode);
    MissionResponse replaceDrone(String missionId, String newDroneCode);
    MissionResponse handoverControl(String missionId, String operatorId);
    MissionResponse startMission(String missionId, String tokenValue);
    MissionResponse startMission(String missionId);
    MissionResponse markReturning(String missionId);
    MissionResponse startPostflightChecking(String missionId);
    MissionResponse completeMission(String missionId);
    MissionResponse failMission(String missionId, String reason);
    MissionResponse updatePostFlightStatus(String missionId, DroneStatus newDroneStatus, String notes);
}
