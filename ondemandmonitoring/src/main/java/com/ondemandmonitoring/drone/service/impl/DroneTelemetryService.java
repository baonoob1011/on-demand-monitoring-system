package com.ondemandmonitoring.drone.service.impl;

import com.ondemandmonitoring.drone.service.IDroneTelemetryService;

import com.ondemandmonitoring.common.exception.ApiException;
import com.ondemandmonitoring.common.exception.ErrorCode;
import com.ondemandmonitoring.drone.domain.Drone;
import com.ondemandmonitoring.drone.dto.request.TelemetryRequest;
import com.ondemandmonitoring.drone.domain.DroneTelemetry;
import com.ondemandmonitoring.drone.enums.DroneStatus;
import com.ondemandmonitoring.drone.repository.DroneRepository;
import com.ondemandmonitoring.drone.repository.DroneTelemetryRepository;
import com.ondemandmonitoring.environment.service.EnvironmentalMeasurementService;
import com.ondemandmonitoring.replanning.event.DroneTelemetrySavedEvent;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class DroneTelemetryService implements IDroneTelemetryService {
    DroneTelemetryRepository droneTelemetryRepository;
    DroneRepository droneRepository;
    EnvironmentalMeasurementService environmentalMeasurementService;
    ApplicationEventPublisher eventPublisher;
    @Transactional
    @Override
    public DroneTelemetry save(String droneCode, TelemetryRequest request) {
        return saveSnapshot(getOrCreateDrone(droneCode), droneCode, request);
    }
    @Transactional
    @Override
    public DroneTelemetry saveForRegisteredDrone(String droneCode, TelemetryRequest request) {
        Drone drone = droneRepository.findByDroneCode(droneCode)
                .orElseThrow(() -> new ApiException(ErrorCode.DRONE_NOT_FOUND,
                        "Registered drone not found: " + droneCode));
        return saveSnapshot(drone, droneCode, request);
    }
    private DroneTelemetry saveSnapshot(Drone drone, String droneCode, TelemetryRequest request) {
        DroneTelemetry telemetry = droneTelemetryRepository.findByDroneCode(droneCode)
                .orElseGet(DroneTelemetry::new);
        telemetry.setDroneCode(droneCode);
        telemetry.setDrone(drone);
        telemetry.setLatitude(request.getLatitude());
        telemetry.setLongitude(request.getLongitude());
        telemetry.setAltitude(request.getAltitude());
        telemetry.setAbsoluteAltitude(request.getAbsoluteAltitude());
        telemetry.setRelativeAltitude(request.getRelativeAltitude());
        telemetry.setSimX(request.getSimX());
        telemetry.setSimY(request.getSimY());
        telemetry.setBatteryPercent(request.getBatteryPercent());
        telemetry.setSpeed(request.getSpeed());
        telemetry.setGpsFixType(request.getGpsFixType());
        telemetry.setGpsSatelliteCount(request.getGpsSatelliteCount());
        telemetry.setGyrometerOk(request.getGyrometerOk());
        telemetry.setAccelerometerOk(request.getAccelerometerOk());
        telemetry.setMagnetometerOk(request.getMagnetometerOk());
        telemetry.setLocalPositionOk(request.getLocalPositionOk());
        telemetry.setGlobalPositionOk(request.getGlobalPositionOk());
        telemetry.setHomePositionOk(request.getHomePositionOk());
        telemetry.setArmable(request.getArmable());
        telemetry.setHeadingDegree(request.getHeadingDegree());
        telemetry.setVelocityNorth(request.getVelocityNorth());
        telemetry.setVelocityEast(request.getVelocityEast());
        telemetry.setVelocityDown(request.getVelocityDown());
        telemetry.setGroundSpeed(request.getGroundSpeed());
        telemetry.setFlightMode(request.getFlightMode());
        telemetry.setArmed(request.getArmed());
        telemetry.setHomeLatitude(request.getHomeLatitude());
        telemetry.setHomeLongitude(request.getHomeLongitude());
        telemetry.setHomeAbsoluteAltitude(request.getHomeAbsoluteAltitude());
        telemetry.setHomeRelativeAltitude(request.getHomeRelativeAltitude());
        telemetry.setRollDegree(request.getRollDegree());
        telemetry.setPitchDegree(request.getPitchDegree());
        telemetry.setYawDegree(request.getYawDegree());
        telemetry.setConnected(request.getConnected());
        telemetry.setInAir(request.getInAir());
        telemetry.setGeofenceConfigured(request.getGeofenceConfigured());
        telemetry.setGeofencePassed(request.getGeofencePassed());

        DroneTelemetry saved = droneTelemetryRepository.save(telemetry);
        environmentalMeasurementService.recordAirPressure(drone, droneCode, request);
        eventPublisher.publishEvent(new DroneTelemetrySavedEvent(saved.getId(), droneCode));
        return saved;
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

