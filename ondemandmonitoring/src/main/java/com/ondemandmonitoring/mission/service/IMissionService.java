package com.ondemandmonitoring.mission.service;

import com.ondemandmonitoring.drone.dto.response.PreflightCheckResponse;
import com.ondemandmonitoring.drone.enums.DroneStatus;
import com.ondemandmonitoring.mission.dto.response.PostflightCheckResponse;
import com.ondemandmonitoring.mission.domain.Mission;
import com.ondemandmonitoring.mission.dto.response.MissionPlanResponse;
import com.ondemandmonitoring.mission.dto.response.MissionResponse;
import com.ondemandmonitoring.mission.dto.response.MissionTelemetryReadinessResponse;
import com.ondemandmonitoring.common.api.PageResponse;
import com.ondemandmonitoring.mission.enums.MissionStatus;
import org.springframework.data.domain.Pageable;
import java.time.Instant;

import java.util.List;

/**
 * Application service interface for mission lifecycle and assignment orchestration (Flow 3).
 * Returns DTOs (MissionResponse) to decouple domain entities from presentation/controller layers.
 */
public interface IMissionService {

    /**
     * Retrieves detailed mission information by ID as a DTO.
     *
     * @param missionId Mission ID
     * @return {@link MissionResponse} Mission details DTO
     */
    MissionResponse getByIdResponse(String missionId);

    /**
     * Retrieves mission information by mission code.
     *
     * @param missionCode Unique mission code
     * @return {@link MissionResponse} Mission details DTO
     */
    MissionResponse getByCodeResponse(String missionCode);

    /**
     * Retrieves all missions assigned to a specific operator.
     *
     * @param operatorId Assigned operator ID
     * @return List of {@link MissionResponse}
     */
    List<MissionResponse> getByOperatorId(String operatorId);

    PageResponse<MissionResponse> searchStaffMissions(MissionStatus status, Instant from, Instant toExclusive, Pageable pageable);

    List<MissionResponse> getCurrentOperatorMissions();

    /**
     * Retrieves all missions pending resource assignment (drone/operator).
     *
     * @return List of {@link MissionResponse}
     */
    List<MissionResponse> getPendingAssignmentMissions();

    /**
     * Retrieves the mission flight plan and waypoints.
     *
     * @param missionId Mission ID
     * @return {@link MissionPlanResponse} Flight plan details
     */
    MissionPlanResponse getMissionPlan(String missionId);

    /**
     * Creates a new monitoring mission from a customer order.
     *
     * @param orderId Order ID
     * @return {@link MissionResponse} Created mission DTO
     */
    MissionResponse createMissionForOrder(String orderId);

    /**
     * Assigns a drone/device to the specified mission.
     *
     * @param missionId Mission ID
     * @param droneId Device/Drone ID
     * @return {@link MissionResponse} Updated mission DTO
     */
    MissionResponse assignDrone(String missionId, String droneId);

    /**
     * Assigns an operator to the specified mission.
     *
     * @param missionId Mission ID
     * @param operatorId Operator ID
     * @return {@link MissionResponse} Updated mission DTO
     */
    MissionResponse assignOperator(String missionId, String operatorId);

    MissionResponse assignResources(String missionId, String droneId, String operatorId);

    MissionResponse acceptCurrentOperatorMission(String missionId);

    MissionResponse rejectCurrentOperatorMission(String missionId, String reason);

    MissionResponse handoverCurrentOperatorControl(String missionId);

    /**
     * Finds and returns the raw Mission domain entity by ID.
     *
     * @param missionId Mission ID
     * @return {@link Mission} Entity
     */
    Mission findById(String missionId);

    /**
     * Accepts a mission assignment by the operator (transitions status to SCHEDULED if flight plan is feasible).
     *
     * @param missionId Mission ID
     * @param operatorId Operator ID
     * @return {@link MissionResponse} Updated mission DTO
     */
    MissionResponse acceptMission(String missionId, String operatorId);

    /**
     * Rejects a mission assignment by the operator (resets mission to RESOURCE_ASSIGNING for re-assignment).
     *
     * @param missionId Mission ID
     * @param operatorId Operator ID
     * @param reason Rejection reason
     * @return {@link MissionResponse} Updated mission DTO
     */
    MissionResponse rejectMission(String missionId, String operatorId, String reason);

    /**
     * Connects GCS app for the mission (Delegated to IGcsConnectionService).
     *
     * @param missionId Mission ID
     * @return {@link MissionResponse} Updated mission DTO
     */
    MissionResponse connectGcs(String missionId);

    /**
     * Disconnects GCS session cleanly (Delegated to IGcsConnectionService).
     *
     * @param missionId Mission ID
     * @param disconnectReason Disconnect reason
     * @return {@link MissionResponse} Updated mission DTO
     */
    MissionResponse disconnectGcs(String missionId, String disconnectReason);

    /**
     * Handles GCS session signal loss and triggers RTL (Delegated to IGcsConnectionService).
     *
     * @param missionId Mission ID
     * @param reason Cause of signal loss
     * @return {@link MissionResponse} Updated mission DTO
     */
    MissionResponse handleGcsSessionLost(String missionId, String reason);

    /**
     * Executes digital preflight safety check before takeoff.
     * Passed -> Issues 60-minute FlightToken and sets status to READY_TO_FLY.
     * Failed -> Logs battery/hardware diagnostics, creates maintenance ticket, and auto-swaps device if available.
     *
     * @param missionId Mission ID
     * @param droneCode Device code
     * @return {@link PreflightCheckResponse} Preflight diagnostics and issued flight token (if passed)
     */
    PreflightCheckResponse runPreflightCheck(String missionId, String droneCode);

    MissionTelemetryReadinessResponse getTelemetryReadiness(String missionId);

    /**
     * Replaces faulty device with a new device for the mission.
     *
     * @param missionId Mission ID
     * @param newDroneCode Replacement device code
     * @return {@link MissionResponse} Updated mission DTO
     */
    MissionResponse replaceDrone(String missionId, String newDroneCode);

    /**
     * Hands over mission control to a new operator (Control Handover).
     *
     * @param missionId Mission ID
     * @param operatorId Target operator ID
     * @return {@link MissionResponse} Updated mission DTO
     */
    MissionResponse handoverControl(String missionId, String operatorId);

    /**
     * Initiates mission flight (Takeoff) with token validation.
     *
     * @param missionId Mission ID
     * @param tokenValue Flight token string
     * @return {@link MissionResponse} Mission set to IN_FLIGHT
     */
    MissionResponse startMission(String missionId, String tokenValue);

    /**
     * Initiates mission flight (Takeoff).
     *
     * @param missionId Mission ID
     * @return {@link MissionResponse} Mission set to IN_FLIGHT
     */
    MissionResponse startMission(String missionId);

    /**
     * Marks the device as returning to base (Return-To-Launch).
     *
     * @param missionId Mission ID
     * @return {@link MissionResponse} Mission set to RETURNING
     */
    MissionResponse markReturning(String missionId);

    /**
     * Starts postflight health inspection for the mission.
     *
     * @param missionId Mission ID
     * @return {@link MissionResponse} Mission set to POST_FLIGHT
     */
    MissionResponse startPostflightChecking(String missionId);

    /**
     * Completes the mission successfully.
     *
     * @param missionId Mission ID
     * @return {@link MissionResponse} Mission set to COMPLETED
     */
    MissionResponse completeMission(String missionId);

    /**
     * Marks mission as failed due to technical or environmental issues.
     *
     * @param missionId Mission ID
     * @param reason Failure cause
     * @return {@link MissionResponse} Mission set to FAILED
     */
    MissionResponse failMission(String missionId, String reason);

    /**
     * Updates postflight status and device health notes.
     *
     * @param missionId Mission ID
     * @param newDroneStatus New device status (e.g., AVAILABLE, MAINTENANCE)
     * @param notes Diagnostic notes
     * @return {@link MissionResponse} Updated mission DTO
     */
    MissionResponse updatePostFlightStatus(String missionId, DroneStatus newDroneStatus, String notes);

    MissionResponse recordPostFlightInspection(
            String missionId,
            DroneStatus newDroneStatus,
            String notes,
            java.util.Map<String, com.ondemandmonitoring.mission.enums.InspectionResult> results,
            com.ondemandmonitoring.mission.dto.request.PostFlightStatusRequest.TelemetrySnapshot telemetrySnapshot);

    PostflightCheckResponse getLatestPostflightCheck(String missionId);
}
