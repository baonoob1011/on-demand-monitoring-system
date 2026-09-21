package com.ondemandmonitoring.mission.service;

import com.ondemandmonitoring.mission.dto.response.MissionResponse;

/**
 * Service interface for managing device connection session lifecycle (`gcs_sessions` / `device_connections`)
 * and handling automated safety signals (e.g., triggering Return-To-Launch on telemetry signal loss).
 */
public interface IDeviceConnectionService {

    /**
     * Establishes a device connection for a given mission.
     * Updates Mission status to CONNECTED, Device status to PREFLIGHT, and creates a new connection session record.
     *
     * @param missionId The unique identifier of the mission
     * @return {@link MissionResponse} DTO containing updated mission state
     */
    MissionResponse connectGcs(String missionId);

    /**
     * Cleanly disconnects an active device connection session.
     * Updates current connection session record status to DISCONNECTED with a specified reason.
     *
     * @param missionId The unique identifier of the mission
     * @param disconnectReason The reason for disconnection (e.g., NORMAL, MISSION_COMPLETED, OPERATOR_EXIT)
     * @return {@link MissionResponse} DTO containing updated mission state
     */
    MissionResponse disconnectGcs(String missionId, String disconnectReason);

    /**
     * Handles telemetry signal loss for an active device connection session.
     * Updates connection session status to LOST, automatically triggers Return-To-Launch (RTL),
     * and sets both Mission and Device status to RETURNING.
     *
     * @param missionId The unique identifier of the mission
     * @param reason The cause of signal loss (e.g., SIGNAL_LOSS_COMM_TIMEOUT)
     * @return {@link MissionResponse} DTO containing updated mission state after triggering RTL
     */
    MissionResponse handleGcsSessionLost(String missionId, String reason);
}
