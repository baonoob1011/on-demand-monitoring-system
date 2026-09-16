package com.ondemandmonitoring.drone.service;

import com.ondemandmonitoring.common.exception.ApiException;
import com.ondemandmonitoring.common.exception.ErrorCode;
import com.ondemandmonitoring.drone.domain.DroneTelemetry;
import com.ondemandmonitoring.drone.domain.Drone;
import com.ondemandmonitoring.drone.domain.PreflightCheck;
import com.ondemandmonitoring.drone.enums.DroneStatus;
import com.ondemandmonitoring.drone.repository.DroneRepository;
import com.ondemandmonitoring.drone.repository.DroneTelemetryRepository;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class PreflightCheckService {

    /** Per activity diagram: Battery >= 80% */
    private static final double MIN_BATTERY_PERCENT = 80.0;
    /** Per state diagram: GPS Lock >= 8 satellites */
    private static final int MIN_GPS_SATELLITES = 8;
    private static final Duration MAX_TELEMETRY_AGE = Duration.ofSeconds(10);
    /** Minimum storage required on drone for a mission (100 MB). */
    private static final long MIN_STORAGE_MB = 100L;

    DroneRepository droneRepository;
    DroneTelemetryRepository droneTelemetryRepository;

    public PreflightCheck run(String droneCode) {
        return run(droneCode, null);
    }

    /**
     * Run pre-flight check in-memory for the given drone and mission.
     * Reads live telemetry sent by the drone via MQTT/telemetry_sender.py,
     * validates all sensor fields, and returns an in-memory {@link PreflightCheck} snapshot.
     *
     * @param droneCode drone drone code
     * @param missionId  mission this check belongs to (nullable for stand-alone checks)
     */
    @Transactional
    public PreflightCheck run(String droneCode, String missionId) {
        Drone drone = getOrCreateDrone(droneCode);
        DroneTelemetry telemetry = droneTelemetryRepository.findByDroneCode(droneCode)
                .orElseThrow(() -> new ApiException(
                        ErrorCode.INVALID_REQUEST,
                        "No telemetry available for drone " + droneCode
                                + ". Start PX4/Gazebo and telemetry_sender.py first, then call preflight again."));

        List<String> failures = validate(telemetry);
        String faultType = classifyFault(telemetry, failures);

        PreflightCheck preflightCheck = fromTelemetry(drone, telemetry);
        preflightCheck.setMissionId(missionId);
        preflightCheck.setOverallPassed(failures.isEmpty());
        preflightCheck.setFailureReason(failures.isEmpty() ? null : String.join("; ", failures));
        preflightCheck.setFaultType(faultType);
        preflightCheck.setCheckedAt(Instant.now());

        return preflightCheck;
    }



    private List<String> validate(DroneTelemetry telemetry) {
        List<String> failures = new ArrayList<>();

        // --- Checklist 5: Telemetry stable (connection + freshness) ---
        requireTrue(failures, telemetry.getConnected(), "PX4/MAVSDK is not connected");
        requireFreshTelemetry(failures, telemetry);
        requirePresent(failures, telemetry.getLatitude(), "Latitude is missing");
        requirePresent(failures, telemetry.getLongitude(), "Longitude is missing");
        requirePresent(failures, telemetry.getRelativeAltitude(), "Relative altitude is missing");

        // --- Checklist 1: Battery >= 80% ---
        requireMinimum(failures, telemetry.getBatteryPercent(), MIN_BATTERY_PERCENT,
                "Battery is below 80% (current: " + telemetry.getBatteryPercent() + "%)");

        // --- Checklist 2: GPS Lock >= 8 satellites ---
        requireGpsFix(failures, telemetry.getGpsFixType());
        requireMinimum(failures, telemetry.getGpsSatelliteCount(), MIN_GPS_SATELLITES,
                "GPS satellite count is below 8 (current: " + telemetry.getGpsSatelliteCount() + ")");

        // --- Core flight sensors (hardware fault indicators) ---
        requireTrue(failures, telemetry.getGyrometerOk(), "Gyrometer is not OK");
        requireTrue(failures, telemetry.getAccelerometerOk(), "Accelerometer is not OK");
        requireTrue(failures, telemetry.getMagnetometerOk(), "Magnetometer is not OK");
        requireTrue(failures, telemetry.getLocalPositionOk(), "Local position is not OK");
        requireTrue(failures, telemetry.getGlobalPositionOk(), "Global position is not OK");
        requireTrue(failures, telemetry.getHomePositionOk(), "Home position is not OK");
        requireTrue(failures, telemetry.getArmable(), "PX4 does not report the drone as armable");
        requireFalse(failures, telemetry.getInAir(), "Drone is already in air");

        return failures;
    }

    /**
     * Classify the type of failure:
     * - "BATTERY" if the only/main failure is low battery (handled by charge station).
     * - "HARDWARE" for any sensor/hardware-level failure (handled by maintenance team).
     * Returns null when there is no failure.
     */
    String classifyFault(DroneTelemetry telemetry, List<String> failures) {
        if (failures.isEmpty()) return null;

        boolean batteryLow = telemetry.getBatteryPercent() != null
                && telemetry.getBatteryPercent() < MIN_BATTERY_PERCENT;

        boolean hardwareFault = !Boolean.TRUE.equals(telemetry.getGyrometerOk())
                || !Boolean.TRUE.equals(telemetry.getAccelerometerOk())
                || !Boolean.TRUE.equals(telemetry.getMagnetometerOk())
                || !Boolean.TRUE.equals(telemetry.getArmable())
                || !Boolean.TRUE.equals(telemetry.getConnected());

        if (hardwareFault) return "HARDWARE";
        if (batteryLow)    return "BATTERY";
        return "HARDWARE"; // default: assume hardware fault for any other issue
    }

    private PreflightCheck fromTelemetry(Drone drone, DroneTelemetry telemetry) {
        PreflightCheck preflightCheck = new PreflightCheck();
        preflightCheck.setDrone(drone);
        preflightCheck.setBatteryPercent(telemetry.getBatteryPercent());
        preflightCheck.setLatitude(telemetry.getLatitude());
        preflightCheck.setLongitude(telemetry.getLongitude());
        preflightCheck.setAbsoluteAltitude(telemetry.getAbsoluteAltitude());
        preflightCheck.setRelativeAltitude(telemetry.getRelativeAltitude());
        preflightCheck.setGpsFixType(telemetry.getGpsFixType());
        preflightCheck.setGpsSatelliteCount(telemetry.getGpsSatelliteCount());
        preflightCheck.setGyrometerOk(telemetry.getGyrometerOk());
        preflightCheck.setAccelerometerOk(telemetry.getAccelerometerOk());
        preflightCheck.setMagnetometerOk(telemetry.getMagnetometerOk());
        preflightCheck.setLocalPositionOk(telemetry.getLocalPositionOk());
        preflightCheck.setGlobalPositionOk(telemetry.getGlobalPositionOk());
        preflightCheck.setHomePositionOk(telemetry.getHomePositionOk());
        preflightCheck.setArmable(telemetry.getArmable());
        preflightCheck.setHeadingDegree(telemetry.getHeadingDegree());
        preflightCheck.setVelocityNorth(telemetry.getVelocityNorth());
        preflightCheck.setVelocityEast(telemetry.getVelocityEast());
        preflightCheck.setVelocityDown(telemetry.getVelocityDown());
        preflightCheck.setGroundSpeed(telemetry.getGroundSpeed());
        preflightCheck.setArmed(telemetry.getArmed());
        preflightCheck.setFlightMode(telemetry.getFlightMode());
        preflightCheck.setHomeLatitude(telemetry.getHomeLatitude());
        preflightCheck.setHomeLongitude(telemetry.getHomeLongitude());
        preflightCheck.setHomeAbsoluteAltitude(telemetry.getHomeAbsoluteAltitude());
        preflightCheck.setHomeRelativeAltitude(telemetry.getHomeRelativeAltitude());
        preflightCheck.setRollDegree(telemetry.getRollDegree());
        preflightCheck.setPitchDegree(telemetry.getPitchDegree());
        preflightCheck.setYawDegree(telemetry.getYawDegree());
        preflightCheck.setConnected(telemetry.getConnected());
        preflightCheck.setInAir(telemetry.getInAir());
        preflightCheck.setGeofenceConfigured(telemetry.getGeofenceConfigured());
        preflightCheck.setGeofencePassed(telemetry.getGeofencePassed());
        return preflightCheck;
    }

    private void requirePresent(List<String> failures, Object value, String message) {
        if (value == null) {
            failures.add(message);
        }
    }

    private void requireTrue(List<String> failures, Boolean value, String message) {
        if (!Boolean.TRUE.equals(value)) {
            failures.add(message);
        }
    }

    private void requireFalse(List<String> failures, Boolean value, String message) {
        if (Boolean.TRUE.equals(value)) {
            failures.add(message);
        }
    }

    private void requireMinimum(List<String> failures, Double value, double minimum, String message) {
        if (value == null || value < minimum) {
            failures.add(message);
        }
    }

    private void requireMinimum(List<String> failures, Integer value, int minimum, String message) {
        if (value == null || value < minimum) {
            failures.add(message);
        }
    }

    private void requireGpsFix(List<String> failures, String gpsFixType) {
        if (gpsFixType == null || gpsFixType.isBlank()) {
            failures.add("GPS fix type is missing");
            return;
        }

        String normalized = gpsFixType.toUpperCase();
        if (!normalized.contains("3D") && !normalized.contains("RTK")) {
            failures.add("GPS fix is not 3D/RTK");
        }
    }

    private void requireFreshTelemetry(List<String> failures, DroneTelemetry telemetry) {
        Instant updatedAt = telemetry.getUpdatedAt();
        if (updatedAt == null) {
            failures.add("Telemetry timestamp is missing");
            return;
        }

        long ageSeconds = Duration.between(updatedAt, Instant.now()).abs().toSeconds();
        if (ageSeconds > MAX_TELEMETRY_AGE.toSeconds()) {
            failures.add("Telemetry is stale (" + ageSeconds + "s old)");
        }
    }

    private Drone getOrCreateDrone(String droneCode) {
        return droneRepository.findByDroneCode(droneCode)
                .orElseGet(() -> {
                    Drone drone = new Drone();
                    drone.setDroneCode(droneCode);
                    drone.setSerialNumber(droneCode);
                    drone.setDroneName("PX4 SITL Drone");
                    
                    drone.setStatus(DroneStatus.AVAILABLE);
                    drone.setLastSeenAt(LocalDateTime.now());
                    return droneRepository.save(drone);
                });
    }
}
