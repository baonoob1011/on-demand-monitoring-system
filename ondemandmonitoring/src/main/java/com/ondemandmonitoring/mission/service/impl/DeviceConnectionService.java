package com.ondemandmonitoring.mission.service.impl;

import com.ondemandmonitoring.common.exception.ApiException;
import com.ondemandmonitoring.common.exception.ErrorCode;
import com.ondemandmonitoring.drone.domain.Drone;
import com.ondemandmonitoring.drone.enums.DroneStatus;
import com.ondemandmonitoring.drone.repository.DroneRepository;
import com.ondemandmonitoring.mission.domain.GcsSession;
import com.ondemandmonitoring.mission.domain.Mission;
import com.ondemandmonitoring.mission.domain.MissionDroneAssignment;
import com.ondemandmonitoring.mission.dto.response.MissionResponse;
import com.ondemandmonitoring.mission.enums.MissionStatus;
import com.ondemandmonitoring.mission.mapper.MissionMapper;
import com.ondemandmonitoring.mission.repository.GcsSessionRepository;
import com.ondemandmonitoring.mission.repository.MissionDroneAssignmentRepository;
import com.ondemandmonitoring.mission.repository.MissionOperatorAssignmentRepository;
import com.ondemandmonitoring.mission.repository.MissionRepository;
import com.ondemandmonitoring.mission.service.IDeviceConnectionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Service implementation managing device connection sessions (`gcs_sessions` / `device_connections`)
 * and automated safety triggers (such as Return-To-Launch on signal loss).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceConnectionService implements IDeviceConnectionService {

    private final MissionRepository missionRepository;
    private final GcsSessionRepository gcsSessionRepository;
    private final MissionDroneAssignmentRepository missionDroneAssignmentRepository;
    private final MissionOperatorAssignmentRepository missionOperatorAssignmentRepository;
    private final DroneRepository droneRepository;
    private final MissionMapper missionMapper;

    @Override
    @Transactional
    public MissionResponse connectGcs(String missionId) {
        Mission mission = missionRepository.findById(missionId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Mission not found: " + missionId));

        if (mission.getStatus() != MissionStatus.SCHEDULED && mission.getStatus() != MissionStatus.CONNECTED) {
            throw new ApiException(ErrorCode.MISSION_STATUS_INVALID,
                    "Mission must be in SCHEDULED state to pair with device, current: " + mission.getStatus());
        }

        mission.setStatus(MissionStatus.CONNECTED);

        Drone device = getAssignedDevice(missionId);
        if (device != null) {
            device.setStatus(DroneStatus.PREFLIGHT);
            droneRepository.save(device);

            GcsSession gcsSession = new GcsSession();
            gcsSession.setMission(mission);
            gcsSession.setDrone(device);
            gcsSession.setOperatorId(getCurrentOperatorId(missionId));
            gcsSession.setConnectionStatus("CONNECTED");
            gcsSession.setTelemetryActive(true);
            gcsSession.setConnectedAt(Instant.now());
            gcsSessionRepository.save(gcsSession);

            missionDroneAssignmentRepository.findByMissionIdAndIsCurrentTrue(missionId)
                    .orElseGet(() -> {
                        MissionDroneAssignment mda = new MissionDroneAssignment();
                        mda.setMission(mission);
                        mda.setDrone(device);
                        mda.setAssignmentSource("MANUAL_MANAGER");
                        mda.setStatus("ACTIVE");
                        mda.setIsCurrent(true);
                        mda.setAssignedAt(Instant.now());
                        return missionDroneAssignmentRepository.save(mda);
                    });
        }

        log.info("Mission {} device connected", missionId);
        Mission saved = missionRepository.save(mission);
        return missionMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public MissionResponse disconnectGcs(String missionId, String disconnectReason) {
        Mission mission = missionRepository.findById(missionId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Mission not found: " + missionId));

        gcsSessionRepository.findTopByMissionIdAndConnectionStatusOrderByConnectedAtDesc(missionId, "CONNECTED")
                .ifPresent(session -> {
                    session.setConnectionStatus("DISCONNECTED");
                    session.setTelemetryActive(false);
                    session.setDisconnectedAt(Instant.now());
                    session.setDisconnectReason(disconnectReason != null && !disconnectReason.isBlank() ? disconnectReason : "NORMAL");
                    gcsSessionRepository.save(session);
                    log.info("[DEVICE-DISCONNECT] Mission {} device session disconnected cleanly. Reason: {}", missionId, session.getDisconnectReason());
                });

        return missionMapper.toResponse(mission);
    }

    @Override
    @Transactional
    public MissionResponse handleGcsSessionLost(String missionId, String reason) {
        Mission mission = missionRepository.findById(missionId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Mission not found: " + missionId));

        gcsSessionRepository.findTopByMissionIdAndConnectionStatusOrderByConnectedAtDesc(missionId, "CONNECTED")
                .ifPresent(session -> {
                    session.setConnectionStatus("LOST");
                    session.setTelemetryActive(false);
                    session.setDisconnectedAt(Instant.now());
                    session.setDisconnectReason(reason != null && !reason.isBlank() ? reason : "SIGNAL_LOSS");
                    gcsSessionRepository.save(session);
                    log.warn("[DEVICE-LOST] Mission {} device telemetry signal LOST. Reason: {}", missionId, session.getDisconnectReason());
                });

        mission.setStatus(MissionStatus.RETURNING);
        Drone device = getAssignedDevice(missionId);
        if (device != null) {
            device.setStatus(DroneStatus.RETURNING);
            droneRepository.save(device);
        }
        log.warn("[RTL-TRIGGER] Mission {} status set to RETURNING due to device signal loss. Device status updated to RETURNING.", missionId);

        Mission saved = missionRepository.save(mission);
        return missionMapper.toResponse(saved);
    }

    private Drone getAssignedDevice(String missionId) {
        return missionDroneAssignmentRepository.findByMissionIdAndIsCurrentTrue(missionId)
                .map(MissionDroneAssignment::getDrone)
                .orElse(null);
    }

    private String getCurrentOperatorId(String missionId) {
        return missionOperatorAssignmentRepository.findByMissionIdAndIsCurrentTrue(missionId)
                .map(mda -> mda.getOperatorId())
                .orElse(null);
    }
}
