package com.ondemandmonitoring.mission.service.impl;

import com.ondemandmonitoring.common.exception.ApiException;
import com.ondemandmonitoring.common.exception.ErrorCode;
import com.ondemandmonitoring.drone.domain.DroneRuntime;
import com.ondemandmonitoring.drone.domain.PreflightCheck;
import com.ondemandmonitoring.drone.dto.response.PreflightCheckResponse;
import com.ondemandmonitoring.drone.enums.DroneOperationalStatus;
import com.ondemandmonitoring.drone.repository.DroneRuntimeRepository;
import com.ondemandmonitoring.drone.service.PreflightCheckService;
import com.ondemandmonitoring.mission.domain.FlightToken;
import com.ondemandmonitoring.mission.domain.Mission;
import com.ondemandmonitoring.mission.dto.response.FlightTokenResponse;
import com.ondemandmonitoring.mission.dto.response.MissionResponse;
import com.ondemandmonitoring.mission.enums.MissionStatus;
import com.ondemandmonitoring.mission.repository.FlightTokenRepository;
import com.ondemandmonitoring.mission.repository.MissionRepository;
import com.ondemandmonitoring.drone.mapper.PreflightCheckMapper;
import com.ondemandmonitoring.mission.mapper.FlightTokenMapper;
import com.ondemandmonitoring.mission.mapper.MissionMapper;
import com.ondemandmonitoring.mission.service.IMissionService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Implementation of {@link IMissionService} for mission lifecycle management.
 * Enterprise pattern: Maps entities to DTOs within @Transactional scope to guarantee safety against LazyInitializationException.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class MissionService implements IMissionService {

    static final long TOKEN_TTL_SECONDS = 900L; // 15 minutes

    MissionRepository missionRepository;
    DroneRuntimeRepository droneRepository;
    FlightTokenRepository flightTokenRepository;
    PreflightCheckService preflightCheckService;
    MissionMapper missionMapper;
    FlightTokenMapper flightTokenMapper;
    PreflightCheckMapper preflightCheckMapper;

    // =========================================================================
    // Query Methods
    // =========================================================================

    @Override
    @Transactional(readOnly = true)
    public MissionResponse getByIdResponse(String missionId) {
        return missionMapper.toResponse(getOrThrow(missionId));
    }

    @Override
    @Transactional(readOnly = true)
    public Mission findById(String missionId) {
        return getOrThrow(missionId);
    }

    // =========================================================================
    // F3.1 – Operator Acceptance / Rejection
    // =========================================================================

    @Override
    @Transactional
    public MissionResponse acceptMission(String missionId, String operatorId) {
        Mission mission = getOrThrow(missionId);
        requireStatus(mission, MissionStatus.WAITING_OPERATOR_ACCEPTANCE);

        mission.setOperatorId(operatorId);
        mission.setStatus(MissionStatus.SCHEDULED);
        log.info("Mission {} accepted by operator {}", missionId, operatorId);
        Mission saved = missionRepository.save(mission);
        return missionMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public MissionResponse rejectMission(String missionId, String operatorId, String reason) {
        Mission mission = getOrThrow(missionId);
        requireStatus(mission, MissionStatus.WAITING_OPERATOR_ACCEPTANCE);

        mission.setOperatorId(operatorId);
        mission.setRejectionReason(reason);
        mission.setStatus(MissionStatus.RESOURCE_ASSIGNING);
        log.warn("Mission {} rejected by operator {} – reason: {}", missionId, operatorId, reason);
        Mission saved = missionRepository.save(mission);
        return missionMapper.toResponse(saved);
    }

    // =========================================================================
    // F3.2 – GCS Pairing & Pre-flight Gate
    // =========================================================================

    @Override
    @Transactional
    public MissionResponse connectGcs(String missionId) {
        Mission mission = getOrThrow(missionId);
        if (mission.getStatus() != MissionStatus.SCHEDULED && mission.getStatus() != MissionStatus.CONNECTED) {
            throw new ApiException(ErrorCode.MISSION_STATUS_INVALID,
                    "Mission must be in SCHEDULED state to pair with GCS, current: " + mission.getStatus());
        }
        mission.setStatus(MissionStatus.CONNECTED);

        if (mission.getDroneRuntime() != null) {
            mission.getDroneRuntime().setStatus(DroneOperationalStatus.PREFLIGHT);
            droneRepository.save(mission.getDroneRuntime());
        }

        log.info("Mission {} – powerOnAndPairWithGCSApp confirmed, status CONNECTED", missionId);
        Mission saved = missionRepository.save(mission);
        return missionMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public PreflightCheckResponse runPreflightCheck(String missionId, String droneCode) {
        Mission mission = getOrThrow(missionId);
        DroneRuntime drone = droneRepository.findByDroneCode(droneCode)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "DroneRuntime not found: " + droneCode));

        if (mission.getStatus() == MissionStatus.SCHEDULED) {
            mission.setStatus(MissionStatus.CONNECTED);
        }

        PreflightCheck check = preflightCheckService.run(droneCode, missionId);
        FlightTokenResponse tokenResponse = null;

        // Store preflight check diagnostics inline on Mission entity (per DB design)
        mission.setPreflightPassed(check.getOverallPassed());
        mission.setPreflightFaultType(check.getFaultType());
        mission.setPreflightFailureReason(check.getFailureReason());
        mission.setPreflightCheckedAt(Instant.now());
        mission.setPreflightRetryCount(
                (mission.getPreflightRetryCount() == null ? 0 : mission.getPreflightRetryCount()) + 1
        );

        if (Boolean.TRUE.equals(check.getOverallPassed())) {
            mission.setStatus(MissionStatus.READY_TO_FLY);
            drone.setStatus(DroneOperationalStatus.PREFLIGHT);
            droneRepository.save(drone);

            FlightToken token = issueFlightToken(missionId, droneCode, mission.getOperatorId());
            tokenResponse = flightTokenMapper.toResponse(token);
            log.info("Mission {} digital preflight PASSED – issued FlightToken {}", missionId, token.getTokenValue());
        } else {
            mission.setStatus(MissionStatus.FAILED_PREFLIGHT);
            handlePreflightFailure(mission, drone, check);
        }

        missionRepository.save(mission);
        return preflightCheckMapper.toResponse(check, tokenResponse);
    }

    private void handlePreflightFailure(Mission mission, DroneRuntime drone, PreflightCheck check) {
        String faultType = check.getFaultType();
        if ("HARDWARE".equalsIgnoreCase(faultType)) {
            drone.setStatus(DroneOperationalStatus.MAINTENANCE);
            log.error("SYSTEM OPERATOR ALERT: Drone {} failed pre-flight check due to HARDWARE fault ({}). Status set to MAINTENANCE.",
                    drone.getDroneCode(), check.getFailureReason());
        } else if ("BATTERY".equalsIgnoreCase(faultType)) {
            drone.setStatus(DroneOperationalStatus.IDLE_CHARGING);
            log.warn("CHARGING STATION ALERT: Drone {} failed pre-flight check due to BATTERY low ({}%). Status set to IDLE_CHARGING.",
                    drone.getDroneCode(), check.getBatteryPercent());
        } else {
            drone.setStatus(DroneOperationalStatus.MAINTENANCE);
        }
        droneRepository.save(drone);

        mission.setStatus(MissionStatus.PENDING_APPROVAL);
        log.warn("Mission {} order status re-queued to PENDING_APPROVAL for Manager re-assignment (Flow 2)", mission.getId());
    }

    private FlightToken issueFlightToken(String missionId, String droneCode, String operatorId) {
        Instant now = Instant.now();
        FlightToken token = new FlightToken();
        token.setTokenValue(com.ondemandmonitoring.mission.util.FlightTokenGenerator.generateTokenValue(missionId, droneCode, operatorId, now));
        token.setMissionId(missionId);
        token.setDroneCode(droneCode);
        token.setOperatorId(operatorId);
        token.setIssuedAt(now);
        token.setExpiresAt(now.plusSeconds(TOKEN_TTL_SECONDS));
        token.setUsed(false);
        token.setRevoked(false);
        return flightTokenRepository.save(token);
    }

    @Override
    @Transactional
    public MissionResponse replaceDrone(String missionId, String newDroneCode) {
        Mission mission = getOrThrow(missionId);
        DroneRuntime newDrone = droneRepository.findByDroneCode(newDroneCode)
                .orElseThrow(() -> new ApiException(ErrorCode.DRONE_NOT_AVAILABLE, "Drone " + newDroneCode + " không tồn tại"));

        if (newDrone.getStatus() != DroneOperationalStatus.AVAILABLE) {
            throw new ApiException(ErrorCode.DRONE_NOT_AVAILABLE, "Drone " + newDroneCode + " is not AVAILABLE (status: " + newDrone.getStatus() + ")");
        }

        List<Mission> activeMissions = missionRepository.findActiveByDroneId(newDrone.getId());
        boolean hasConflict = activeMissions.stream().anyMatch(m -> !m.getId().equals(missionId));
        if (hasConflict) {
            throw new ApiException(ErrorCode.SCHEDULE_CONFLICT, "Drone " + newDroneCode + " đang được lên lịch cho chuyến bay khác");
        }

        DroneRuntime oldDrone = mission.getDroneRuntime();
        if (oldDrone != null) {
            oldDrone.setStatus(DroneOperationalStatus.MAINTENANCE);
            droneRepository.save(oldDrone);
        }

        newDrone.setStatus(DroneOperationalStatus.PREFLIGHT);
        droneRepository.save(newDrone);

        mission.setDroneRuntime(newDrone);
        mission.setStatus(MissionStatus.CONNECTED);
        log.info("Mission {} – replaced drone with {}, status reset to CONNECTED", missionId, newDroneCode);
        Mission saved = missionRepository.save(mission);
        return missionMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public MissionResponse handoverControl(String missionId, String operatorId) {
        Mission mission = getOrThrow(missionId);
        requireStatus(mission, MissionStatus.READY_TO_FLY);
        mission.setOperatorId(operatorId);
        log.info("Mission {} – control handed over to operator {} at {}", missionId, operatorId, Instant.now());
        Mission saved = missionRepository.save(mission);
        return missionMapper.toResponse(saved);
    }

    // =========================================================================
    // F3.3 – Takeoff & Execution lifecycle
    // =========================================================================

    @Override
    @Transactional
    public MissionResponse startMission(String missionId, String tokenValue) {
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
        updateDroneOperationalStatus(mission, DroneOperationalStatus.ACTIVE_MISSION);

        log.info("Mission {} IN_FLIGHT – WebSocket telemetry and RTSP video stream OPENED", missionId);
        Mission saved = missionRepository.save(mission);
        return missionMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public MissionResponse startMission(String missionId) {
        return startMission(missionId, null);
    }

    @Override
    @Transactional
    public MissionResponse markReturning(String missionId) {
        Mission mission = getOrThrow(missionId);
        if (mission.getStatus() != MissionStatus.IN_FLIGHT && mission.getStatus() != MissionStatus.IN_PROGRESS) {
            throw new ApiException(ErrorCode.MISSION_STATUS_INVALID,
                    "Mission must be IN_FLIGHT to mark returning, current: " + mission.getStatus());
        }
        mission.setStatus(MissionStatus.RETURNING);
        updateDroneOperationalStatus(mission, DroneOperationalStatus.RETURNING);
        log.info("Mission {} – drone returning to base", missionId);
        Mission saved = missionRepository.save(mission);
        return missionMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public MissionResponse startPostflightChecking(String missionId) {
        Mission mission = getOrThrow(missionId);
        requireStatus(mission, MissionStatus.RETURNING);
        mission.setStatus(MissionStatus.POSTFLIGHT_CHECKING);
        log.info("Mission {} – post-flight inspection started", missionId);
        Mission saved = missionRepository.save(mission);
        return missionMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public MissionResponse completeMission(String missionId) {
        Mission mission = getOrThrow(missionId);
        requireStatus(mission, MissionStatus.POSTFLIGHT_CHECKING);
        mission.setStatus(MissionStatus.COMPLETED);
        mission.setCompletedAt(Instant.now());
        updateDroneOperationalStatus(mission, DroneOperationalStatus.AVAILABLE);
        log.info("Mission {} COMPLETED successfully", missionId);
        Mission saved = missionRepository.save(mission);
        return missionMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public MissionResponse failMission(String missionId, String reason) {
        Mission mission = getOrThrow(missionId);
        mission.setStatus(MissionStatus.FAILED);
        mission.setFailureReason(reason);
        updateDroneOperationalStatus(mission, DroneOperationalStatus.AVAILABLE);
        log.error("Mission {} FAILED – reason: {}", missionId, reason);
        Mission saved = missionRepository.save(mission);
        return missionMapper.toResponse(saved);
    }

    // =========================================================================
    // F3.5 – Post-flight drone status update
    // =========================================================================

    @Override
    @Transactional
    public MissionResponse updatePostFlightStatus(String missionId, DroneOperationalStatus newDroneOperationalStatus, String notes) {
        Mission mission = getOrThrow(missionId);
        DroneRuntime drone = mission.getDroneRuntime();
        if (drone == null) {
            throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Mission " + missionId + " has no assigned drone");
        }
        drone.setStatus(newDroneOperationalStatus);
        droneRepository.save(drone);

        if (mission.getStatus() == MissionStatus.POSTFLIGHT_CHECKING) {
            mission.setStatus(MissionStatus.COMPLETED);
            mission.setCompletedAt(Instant.now());
        }

        if (notes != null && !notes.isBlank()) {
            log.info("Mission {} post-flight notes: {}", missionId, notes);
        }
        log.info("Mission {} post-flight completed – drone {} status set to {}", missionId, drone.getDroneCode(), newDroneOperationalStatus);
        Mission saved = missionRepository.save(mission);
        return missionMapper.toResponse(saved);
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

    private void updateDroneOperationalStatus(Mission mission, DroneOperationalStatus newStatus) {
        DroneRuntime drone = mission.getDroneRuntime();
        if (drone != null) {
            drone.setStatus(newStatus);
            droneRepository.save(drone);
        }
    }
}
