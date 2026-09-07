package com.ondemandmonitoring.mission.service;

import com.ondemandmonitoring.device.dto.response.PreflightCheckResponse;
import com.ondemandmonitoring.device.enums.DeviceStatus;
import com.ondemandmonitoring.mission.domain.Mission;

/**
 * Application service interface for mission lifecycle and assignment orchestration (Flow 3).
 */
public interface IMissionService {

    /**
     * Operator confirms they accept the assigned mission.
     * Transitions: WAITING_OPERATOR_ACCEPTANCE -> SCHEDULED
     *
     * @param missionId  Target mission ID
     * @param operatorId Operator user ID
     * @return Updated Mission entity
     */
    Mission acceptMission(String missionId, String operatorId);

    /**
     * Operator rejects the assigned mission with a mandatory reason.
     * Transitions: WAITING_OPERATOR_ACCEPTANCE -> RESOURCE_ASSIGNING
     *
     * @param missionId  Target mission ID
     * @param operatorId Operator user ID
     * @param reason     Mandatory rejection reason
     * @return Updated Mission entity
     */
    Mission rejectMission(String missionId, String operatorId, String reason);

    /**
     * Confirms telemetry link between GCS App and drone.
     * Transitions: SCHEDULED -> CONNECTED
     *
     * @param missionId Target mission ID
     * @return Updated Mission entity
     */
    Mission connectGcs(String missionId);

    /**
     * Runs digital 6-point preflight checklist (Battery >= 80%, GPS >= 8 sats, Camera/Gimbal, Storage, Weather).
     * If passed, issues Flight Access Token and transitions mission -> READY_TO_FLY.
     * If failed, classifies fault and routes order -> PENDING_APPROVAL.
     *
     * @param missionId  Target mission ID
     * @param deviceCode Drone device code
     * @return PreflightCheckResponse containing result and optional FlightToken
     */
    PreflightCheckResponse runPreflightCheck(String missionId, String deviceCode);

    /**
     * Replaces assigned drone with a new available drone when preflight check fails.
     * Resets mission status to CONNECTED so preflight can be re-run.
     *
     * @param missionId     Target mission ID
     * @param newDeviceCode Replacement drone device code
     * @return Updated Mission entity
     */
    Mission replaceDrone(String missionId, String newDeviceCode);

    /**
     * Operator formally accepts control of the drone console before takeoff.
     *
     * @param missionId  Target mission ID
     * @param operatorId Operator user ID
     * @return Updated Mission entity
     */
    Mission handoverControl(String missionId, String operatorId);

    /**
     * Validates Flight Access Token and starts mission execution (takeoff).
     * Transitions: READY_TO_FLY -> IN_FLIGHT
     *
     * @param missionId  Target mission ID
     * @param tokenValue Issued flight access token string
     * @return Updated Mission entity
     */
    Mission startMission(String missionId, String tokenValue);

    /**
     * Overload method for startMission without explicit token string.
     *
     * @param missionId Target mission ID
     * @return Updated Mission entity
     */
    Mission startMission(String missionId);

    /**
     * Drone finishes data capture and heads back to base.
     * Transitions: IN_FLIGHT -> RETURNING
     *
     * @param missionId Target mission ID
     * @return Updated Mission entity
     */
    Mission markReturning(String missionId);

    /**
     * Drone lands, operator starts post-flight inspection.
     * Transitions: RETURNING -> POSTFLIGHT_CHECKING
     *
     * @param missionId Target mission ID
     * @return Updated Mission entity
     */
    Mission startPostflightChecking(String missionId);

    /**
     * Operator confirms mission completed successfully.
     * Transitions: POSTFLIGHT_CHECKING -> COMPLETED
     *
     * @param missionId Target mission ID
     * @return Updated Mission entity
     */
    Mission completeMission(String missionId);

    /**
     * Operator reports mission failure during flight.
     * Transitions -> FAILED
     *
     * @param missionId Target mission ID
     * @param reason    Failure description
     * @return Updated Mission entity
     */
    Mission failMission(String missionId, String reason);

    /**
     * Updates physical drone health status after landing.
     *
     * @param missionId       Target mission ID
     * @param newDeviceStatus New device status (AVAILABLE, MAINTENANCE, etc.)
     * @param notes           Inspection notes
     * @return Updated Mission entity
     */
    Mission updatePostFlightStatus(String missionId, DeviceStatus newDeviceStatus, String notes);

    /**
     * Finds mission by ID or throws exception.
     *
     * @param missionId Target mission ID
     * @return Mission entity
     */
    Mission findById(String missionId);
}
