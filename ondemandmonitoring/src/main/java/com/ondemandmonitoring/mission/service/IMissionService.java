package com.ondemandmonitoring.mission.service;

import com.ondemandmonitoring.drone.dto.response.PreflightCheckResponse;
import com.ondemandmonitoring.drone.enums.DroneOperationalStatus;
import com.ondemandmonitoring.mission.domain.Mission;
import com.ondemandmonitoring.mission.dto.response.MissionResponse;

/**
 * Application service interface for mission lifecycle and assignment orchestration (Flow 3).
 * Returns DTOs (MissionResponse) to decouple domain entities from presentation/controller layers.
 */
public interface IMissionService {

    /**
     * Finds mission by ID and returns its DTO response.
     *
     * @param missionId Target mission ID
     * @return MissionResponse DTO
     */
    MissionResponse getByIdResponse(String missionId);

    /**
     * Finds mission entity by ID (internal service usage).
     *
     * @param missionId Target mission ID
     * @return Mission entity
     */
    Mission findById(String missionId);

    /**
     * Operator confirms they accept the assigned mission.
     * Transitions: WAITING_OPERATOR_ACCEPTANCE -> SCHEDULED
     *
     * @param missionId  Target mission ID
     * @param operatorId Operator user ID
     * @return MissionResponse DTO
     */
    MissionResponse acceptMission(String missionId, String operatorId);

    /**
     * Operator rejects the assigned mission with a mandatory reason.
     * Transitions: WAITING_OPERATOR_ACCEPTANCE -> RESOURCE_ASSIGNING
     *
     * @param missionId  Target mission ID
     * @param operatorId Operator user ID
     * @param reason     Mandatory rejection reason
     * @return MissionResponse DTO
     */
    MissionResponse rejectMission(String missionId, String operatorId, String reason);

    /**
     * Confirms telemetry link between GCS App and drone.
     * Transitions: SCHEDULED -> CONNECTED
     *
     * @param missionId Target mission ID
     * @return MissionResponse DTO
     */
    MissionResponse connectGcs(String missionId);

    /**
     * Runs digital 6-point preflight checklist (Battery >= 80%, GPS >= 8 sats, Camera/Gimbal, Storage, Weather).
     * If passed, issues Flight Access Token and transitions mission -> READY_TO_FLY.
     * If failed, classifies fault and routes order -> PENDING_APPROVAL.
     *
     * @param missionId  Target mission ID
     * @param droneCode Drone drone code
     * @return PreflightCheckResponse containing result and optional FlightToken
     */
    PreflightCheckResponse runPreflightCheck(String missionId, String droneCode);

    /**
     * Replaces assigned drone with a new available drone when preflight check fails.
     * Resets mission status to CONNECTED so preflight can be re-run.
     *
     * @param missionId     Target mission ID
     * @param newDroneCode Replacement drone drone code
     * @return MissionResponse DTO
     */
    MissionResponse replaceDrone(String missionId, String newDroneCode);

    /**
     * Operator formally accepts control of the drone console before takeoff.
     *
     * @param missionId  Target mission ID
     * @param operatorId Operator user ID
     * @return MissionResponse DTO
     */
    MissionResponse handoverControl(String missionId, String operatorId);

    /**
     * Validates Flight Access Token and starts mission execution (takeoff).
     * Transitions: READY_TO_FLY -> IN_FLIGHT
     *
     * @param missionId  Target mission ID
     * @param tokenValue Issued flight access token string
     * @return MissionResponse DTO
     */
    MissionResponse startMission(String missionId, String tokenValue);

    /**
     * Overload method for startMission without explicit token string.
     *
     * @param missionId Target mission ID
     * @return MissionResponse DTO
     */
    MissionResponse startMission(String missionId);

    /**
     * Drone finishes data capture and heads back to base.
     * Transitions: IN_FLIGHT -> RETURNING
     *
     * @param missionId Target mission ID
     * @return MissionResponse DTO
     */
    MissionResponse markReturning(String missionId);

    /**
     * Drone lands, operator starts post-flight inspection.
     * Transitions: RETURNING -> POSTFLIGHT_CHECKING
     *
     * @param missionId Target mission ID
     * @return MissionResponse DTO
     */
    MissionResponse startPostflightChecking(String missionId);

    /**
     * Operator confirms mission completed successfully.
     * Transitions: POSTFLIGHT_CHECKING -> COMPLETED
     *
     * @param missionId Target mission ID
     * @return MissionResponse DTO
     */
    MissionResponse completeMission(String missionId);

    /**
     * Operator reports mission failure during flight.
     * Transitions -> FAILED
     *
     * @param missionId Target mission ID
     * @param reason    Failure description
     * @return MissionResponse DTO
     */
    MissionResponse failMission(String missionId, String reason);

    /**
     * Updates physical drone health status after landing.
     *
     * @param missionId       Target mission ID
     * @param newDroneOperationalStatus New drone status (AVAILABLE, MAINTENANCE, etc.)
     * @param notes           Inspection notes
     * @return MissionResponse DTO
     */
    MissionResponse updatePostFlightStatus(String missionId, DroneOperationalStatus newDroneOperationalStatus, String notes);
}
