package com.ondemandmonitoring.mission.service.impl;

import com.ondemandmonitoring.common.exception.ApiException;
import com.ondemandmonitoring.common.exception.ErrorCode;
import com.ondemandmonitoring.device.domain.Device;
import com.ondemandmonitoring.device.domain.PreflightCheck;
import com.ondemandmonitoring.device.dto.response.PreflightCheckResponse;
import com.ondemandmonitoring.device.enums.DeviceStatus;
import com.ondemandmonitoring.device.repository.DeviceRepository;
import com.ondemandmonitoring.device.service.PreflightCheckService;
import com.ondemandmonitoring.mission.domain.FlightToken;
import com.ondemandmonitoring.mission.domain.Mission;
import com.ondemandmonitoring.mission.dto.response.FlightTokenResponse;
import com.ondemandmonitoring.mission.enums.MissionStatus;
import com.ondemandmonitoring.mission.repository.FlightTokenRepository;
import com.ondemandmonitoring.mission.repository.MissionRepository;
import com.ondemandmonitoring.device.mapper.PreflightCheckMapper;
import com.ondemandmonitoring.mission.mapper.FlightTokenMapper;
import com.ondemandmonitoring.mission.service.IMissionService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Implementation of {@link IMissionService} for mission lifecycle management.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class MissionService implements IMissionService {

    static final long TOKEN_TTL_SECONDS = 900L; // 15 minutes

    MissionRepository missionRepository;
    DeviceRepository deviceRepository;
    FlightTokenRepository flightTokenRepository;
    PreflightCheckService preflightCheckService;
    FlightTokenMapper flightTokenMapper;
    PreflightCheckMapper preflightCheckMapper;

    // =========================================================================
    // F3.1 – Operator Acceptance / Rejection
    // =========================================================================

    @Override
    @Transactional
    public Mission acceptMission(String missionId, String operatorId) {
        Mission mission = getOrThrow(missionId);
        requireStatus(mission, MissionStatus.WAITING_OPERATOR_ACCEPTANCE);

        mission.setOperatorId(operatorId);
        mission.setStatus(MissionStatus.SCHEDULED);
        log.info("Mission {} accepted by operator {}", missionId, operatorId);
        return missionRepository.save(mission);
    }

    @Override
    @Transactional
    public Mission rejectMission(String missionId, String operatorId, String reason) {
        Mission mission = getOrThrow(missionId);
        requireStatus(mission, MissionStatus.WAITING_OPERATOR_ACCEPTANCE);

        mission.setOperatorId(operatorId);
        mission.setRejectionReason(reason);
        mission.setStatus(MissionStatus.RESOURCE_ASSIGNING);
        log.warn("Mission {} rejected by operator {} – reason: {}", missionId, operatorId, reason);
        return missionRepository.save(mission);
    }

    // =========================================================================
    // F3.2 – GCS Pairing & Pre-flight Gate
    // =========================================================================

    @Override
    @Transactional
    public Mission connectGcs(String missionId) {
        Mission mission = getOrThrow(missionId);
        if (mission.getStatus() != MissionStatus.SCHEDULED && mission.getStatus() != MissionStatus.CONNECTED) {
            throw new ApiException(ErrorCode.MISSION_STATUS_INVALID,
                    "Mission must be in SCHEDULED state to pair with GCS, current: " + mission.getStatus());
        }
        mission.setStatus(MissionStatus.CONNECTED);

        if (mission.getDevice() != null) {
            mission.getDevice().setStatus(DeviceStatus.PREFLIGHT);
            deviceRepository.save(mission.getDevice());
        }

        log.info("Mission {} – powerOnAndPairWithGCSApp confirmed, status CONNECTED", missionId);
        return missionRepository.save(mission);
    }

    @Override
    @Transactional
    public PreflightCheckResponse runPreflightCheck(String missionId, String deviceCode) {
        Mission mission = getOrThrow(missionId);
        Device drone = deviceRepository.findByDeviceCode(deviceCode)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Device not found: " + deviceCode));

        if (mission.getStatus() == MissionStatus.SCHEDULED) {
            mission.setStatus(MissionStatus.CONNECTED);
        }

        PreflightCheck check = preflightCheckService.run(deviceCode, missionId);
        FlightTokenResponse tokenResponse = null;

        if (Boolean.TRUE.equals(check.getOverallPassed())) {
            mission.setStatus(MissionStatus.READY_TO_FLY);
            drone.setStatus(DeviceStatus.PREFLIGHT);
            deviceRepository.save(drone);

            FlightToken token = issueFlightToken(missionId, deviceCode, mission.getOperatorId());
            tokenResponse = flightTokenMapper.toResponse(token);
            log.info("Mission {} digital preflight PASSED – issued FlightToken {}", missionId, token.getTokenValue());
        } else {
            mission.setStatus(MissionStatus.FAILED_PREFLIGHT);
            handlePreflightFailure(mission, drone, check);
        }

        missionRepository.save(mission);
        return preflightCheckMapper.toResponse(check, tokenResponse);
    }

    private void handlePreflightFailure(Mission mission, Device drone, PreflightCheck check) {
        String faultType = check.getFaultType();
        if ("HARDWARE".equalsIgnoreCase(faultType)) {
            drone.setStatus(DeviceStatus.MAINTENANCE);
            log.error("SYSTEM OPERATOR ALERT: Drone {} failed pre-flight check due to HARDWARE fault ({}). Status set to MAINTENANCE.",
                    drone.getDeviceCode(), check.getFailureReason());
        } else if ("BATTERY".equalsIgnoreCase(faultType)) {
            drone.setStatus(DeviceStatus.IDLE_CHARGING);
            log.warn("CHARGING STATION ALERT: Drone {} failed pre-flight check due to BATTERY low ({}%). Status set to IDLE_CHARGING.",
                    drone.getDeviceCode(), check.getBatteryPercent());
        } else {
            drone.setStatus(DeviceStatus.MAINTENANCE);
        }
        deviceRepository.save(drone);

        mission.setStatus(MissionStatus.PENDING_APPROVAL);
        log.warn("Mission {} order status re-queued to PENDING_APPROVAL for Manager re-assignment", mission.getId());
    }

    private FlightToken issueFlightToken(String missionId, String deviceCode, String operatorId) {
        FlightToken token = new FlightToken();
        token.setTokenValue(UUID.randomUUID().toString());
        token.setMissionId(missionId);
        token.setDeviceCode(deviceCode);
        token.setOperatorId(operatorId);
        token.setIssuedAt(Instant.now());
        token.setExpiresAt(Instant.now().plusSeconds(TOKEN_TTL_SECONDS));
        token.setUsed(false);
        token.setRevoked(false);
        return flightTokenRepository.save(token);
    }

    @Override
    @Transactional
    public Mission replaceDrone(String missionId, String newDeviceCode) {
        Mission mission = getOrThrow(missionId);
        Device newDrone = deviceRepository.findByDeviceCode(newDeviceCode)
                .orElseThrow(() -> new ApiException(ErrorCode.DRONE_NOT_AVAILABLE, "Drone " + newDeviceCode + " không tồn tại"));

        if (newDrone.getStatus() != DeviceStatus.AVAILABLE) {
            throw new ApiException(ErrorCode.DRONE_NOT_AVAILABLE, "Drone " + newDeviceCode + " is not AVAILABLE (status: " + newDrone.getStatus() + ")");
        }

        List<Mission> activeMissions = missionRepository.findActiveByDeviceId(newDrone.getId());
        boolean hasConflict = activeMissions.stream().anyMatch(m -> !m.getId().equals(missionId));
        if (hasConflict) {
            throw new ApiException(ErrorCode.SCHEDULE_CONFLICT, "Drone " + newDeviceCode + " đang được lên lịch cho chuyến bay khác");
        }

        Device oldDrone = mission.getDevice();
        if (oldDrone != null) {
            oldDrone.setStatus(DeviceStatus.MAINTENANCE);
            deviceRepository.save(oldDrone);
        }

        newDrone.setStatus(DeviceStatus.PREFLIGHT);
        deviceRepository.save(newDrone);

        mission.setDevice(newDrone);
        mission.setStatus(MissionStatus.CONNECTED);
        log.info("Mission {} – replaced drone with {}, status reset to CONNECTED", missionId, newDeviceCode);
        return missionRepository.save(mission);
    }

    @Override
    @Transactional
    public Mission handoverControl(String missionId, String operatorId) {
        Mission mission = getOrThrow(missionId);
        requireStatus(mission, MissionStatus.READY_TO_FLY);
        mission.setOperatorId(operatorId);
        log.info("Mission {} – control handed over to operator {} at {}", missionId, operatorId, Instant.now());
        return missionRepository.save(mission);
    }

    // =========================================================================
    // F3.3 – Takeoff & Execution lifecycle
    // =========================================================================

    @Override
    @Transactional
    public Mission startMission(String missionId, String tokenValue) {
        Mission mission = getOrThrow(missionId);
        requireStatus(mission, MissionStatus.READY_TO_FLY);

        if (tokenValue != null && !tokenValue.isBlank()) {
            FlightToken token = flightTokenRepository.findByTokenValue(tokenValue)
                    .orElseThrow(() -> new ApiException(ErrorCode.INVALID_REQUEST, "Invalid flight access token"));

            if (!token.isValid()) {
                token.setRevoked(true);
                flightTokenRepository.save(token);
                log.warn("Mission {} flight token EXPIRED or ALREADY USED – revoking token", missionId);
                throw new ApiException(ErrorCode.INVALID_REQUEST,
                        "Flight token is expired or revoked. Please re-run preflight check.");
            }
            token.setUsed(true);
            flightTokenRepository.save(token);
        } else {
            FlightToken token = flightTokenRepository.findByMissionIdAndUsedFalseAndRevokedFalse(missionId)
                    .orElse(null);
            if (token != null) {
                if (!token.isValid()) {
                    token.setRevoked(true);
                    flightTokenRepository.save(token);
                    throw new ApiException(ErrorCode.INVALID_REQUEST,
                            "Flight token is expired. Please re-run preflight check.");
                }
                token.setUsed(true);
                flightTokenRepository.save(token);
            }
        }

        mission.setStatus(MissionStatus.IN_FLIGHT);
        mission.setStartedAt(Instant.now());
        updateDeviceStatus(mission, DeviceStatus.ACTIVE_MISSION);

        log.info("Mission {} IN_FLIGHT – WebSocket telemetry and RTSP video stream OPENED", missionId);
        return missionRepository.save(mission);
    }

    @Override
    @Transactional
    public Mission startMission(String missionId) {
        return startMission(missionId, null);
    }

    @Override
    @Transactional
    public Mission markReturning(String missionId) {
        Mission mission = getOrThrow(missionId);
        if (mission.getStatus() != MissionStatus.IN_FLIGHT && mission.getStatus() != MissionStatus.IN_PROGRESS) {
            throw new ApiException(ErrorCode.MISSION_STATUS_INVALID,
                    "Mission must be IN_FLIGHT to mark returning, current: " + mission.getStatus());
        }
        mission.setStatus(MissionStatus.RETURNING);
        updateDeviceStatus(mission, DeviceStatus.RETURNING);
        log.info("Mission {} – drone returning to base", missionId);
        return missionRepository.save(mission);
    }

    @Override
    @Transactional
    public Mission startPostflightChecking(String missionId) {
        Mission mission = getOrThrow(missionId);
        requireStatus(mission, MissionStatus.RETURNING);
        mission.setStatus(MissionStatus.POSTFLIGHT_CHECKING);
        log.info("Mission {} – post-flight inspection started", missionId);
        return missionRepository.save(mission);
    }

    @Override
    @Transactional
    public Mission completeMission(String missionId) {
        Mission mission = getOrThrow(missionId);
        requireStatus(mission, MissionStatus.POSTFLIGHT_CHECKING);
        mission.setStatus(MissionStatus.COMPLETED);
        mission.setCompletedAt(Instant.now());
        updateDeviceStatus(mission, DeviceStatus.AVAILABLE);
        log.info("Mission {} COMPLETED successfully", missionId);
        return missionRepository.save(mission);
    }

    @Override
    @Transactional
    public Mission failMission(String missionId, String reason) {
        Mission mission = getOrThrow(missionId);
        mission.setStatus(MissionStatus.FAILED);
        mission.setFailureReason(reason);
        updateDeviceStatus(mission, DeviceStatus.AVAILABLE);
        log.error("Mission {} FAILED – reason: {}", missionId, reason);
        return missionRepository.save(mission);
    }

    // =========================================================================
    // F3.5 – Post-flight device status update
    // =========================================================================

    @Override
    @Transactional
    public Mission updatePostFlightStatus(String missionId, DeviceStatus newDeviceStatus, String notes) {
        Mission mission = getOrThrow(missionId);
        Device device = mission.getDevice();
        if (device == null) {
            throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Mission " + missionId + " has no assigned drone");
        }
        device.setStatus(newDeviceStatus);
        deviceRepository.save(device);

        if (mission.getStatus() == MissionStatus.POSTFLIGHT_CHECKING) {
            mission.setStatus(MissionStatus.COMPLETED);
            mission.setCompletedAt(Instant.now());
        }

        if (notes != null && !notes.isBlank()) {
            log.info("Mission {} post-flight notes: {}", missionId, notes);
        }
        log.info("Mission {} post-flight completed – drone {} status set to {}", missionId, device.getDeviceCode(), newDeviceStatus);
        return missionRepository.save(mission);
    }

    // =========================================================================
    // Query
    // =========================================================================

    @Override
    @Transactional(readOnly = true)
    public Mission findById(String missionId) {
        return getOrThrow(missionId);
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private Mission getOrThrow(String missionId) {
        return missionRepository.findById(missionId)
                .orElseThrow(() -> new ApiException(ErrorCode.MISSION_NOT_FOUND,
                        "Mission not found: " + missionId));
    }

    private void requireStatus(Mission mission, MissionStatus expected) {
        if (mission.getStatus() != expected) {
            throw new ApiException(ErrorCode.MISSION_STATUS_INVALID,
                    "Mission status must be " + expected + " but is " + mission.getStatus());
        }
    }

    private void updateDeviceStatus(Mission mission, DeviceStatus newStatus) {
        Device device = mission.getDevice();
        if (device != null) {
            device.setStatus(newStatus);
            deviceRepository.save(device);
        }
    }
}
