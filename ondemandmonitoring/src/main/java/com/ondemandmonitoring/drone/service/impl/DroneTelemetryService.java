package com.ondemandmonitoring.drone.service.impl;

import com.ondemandmonitoring.drone.service.IDroneTelemetryService;

import com.ondemandmonitoring.common.exception.ApiException;
import com.ondemandmonitoring.common.exception.ErrorCode;
import com.ondemandmonitoring.device.domain.Device;
import com.ondemandmonitoring.device.enums.DeviceStatus;
import com.ondemandmonitoring.device.repository.DeviceRepository;
import com.ondemandmonitoring.drone.dto.request.TelemetryRequest;
import com.ondemandmonitoring.drone.domain.DeviceTelemetry;
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
    DeviceRepository deviceRepository;
    EnvironmentalMeasurementService environmentalMeasurementService;
    ApplicationEventPublisher eventPublisher;

    @Transactional
    @Override
    public DeviceTelemetry save(String droneCode, TelemetryRequest request) {
        return saveSnapshot(getOrCreateDevice(droneCode), droneCode, request);
    }

    @Transactional
    @Override
    public DeviceTelemetry saveForRegisteredDrone(String deviceCode, TelemetryRequest request) {
        Device device = deviceRepository.findByDeviceCode(deviceCode)
                .orElseThrow(() -> new ApiException(ErrorCode.DEVICE_NOT_FOUND,
                        "Registered device not found: " + deviceCode));
        return saveSnapshot(device, deviceCode, request);
    }

    private DeviceTelemetry saveSnapshot(Device device, String deviceCode, TelemetryRequest request) {
        DeviceTelemetry telemetry = droneTelemetryRepository.findByDeviceCode(deviceCode)
                .orElseGet(DeviceTelemetry::new);
        telemetry.setDeviceCode(deviceCode);
        telemetry.setDevice(device);
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

        DeviceTelemetry saved = droneTelemetryRepository.save(telemetry);
        environmentalMeasurementService.recordAirPressure(device, deviceCode, request);
        eventPublisher.publishEvent(new DroneTelemetrySavedEvent(saved.getId(), deviceCode));
        return saved;
    }

    private Device getOrCreateDevice(String deviceCode) {
        return deviceRepository.findByDeviceCode(deviceCode)
                .orElseGet(() -> {
                    Device device = new Device();
                    device.setDeviceCode(deviceCode);
                    device.setSerialNumber(deviceCode);
                    device.setName("PX4 SITL Drone");
                    device.setStatus(DeviceStatus.AVAILABLE);
                    device.setLastSeenAt(LocalDateTime.now());
                    return deviceRepository.save(device);
                });
    }
}
