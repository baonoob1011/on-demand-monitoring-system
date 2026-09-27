package com.ondemandmonitoring.device.service.impl;

import com.ondemandmonitoring.device.service.IDeviceTelemetryService;

import com.ondemandmonitoring.common.exception.ApiException;
import com.ondemandmonitoring.common.exception.ErrorCode;
import com.ondemandmonitoring.device.domain.Device;
import com.ondemandmonitoring.device.dto.request.TelemetryRequest;
import com.ondemandmonitoring.device.domain.DeviceTelemetry;
import com.ondemandmonitoring.device.repository.DeviceRepository;
import com.ondemandmonitoring.device.repository.DroneRepository;
import com.ondemandmonitoring.device.repository.DeviceTelemetryRepository;
import com.ondemandmonitoring.environment.service.EnvironmentalMeasurementService;
import com.ondemandmonitoring.replanning.event.DeviceTelemetrySavedEvent;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class DeviceTelemetryService implements IDeviceTelemetryService {
    DeviceTelemetryRepository DeviceTelemetryRepository;
    DeviceRepository deviceRepository;
    DroneRepository droneRepository;
    EnvironmentalMeasurementService environmentalMeasurementService;
    ApplicationEventPublisher eventPublisher;
    @Transactional
    @Override
    public DeviceTelemetry save(String deviceCode, TelemetryRequest request) {
        return saveSnapshot(resolveRegisteredDevice(deviceCode), deviceCode, request);
    }
    @Transactional
    @Override
    public DeviceTelemetry saveForRegisteredDevice(String deviceCode, TelemetryRequest request) {
        return saveSnapshot(resolveRegisteredDevice(deviceCode), deviceCode, request);
    }
    private DeviceTelemetry saveSnapshot(Device device, String deviceCode, TelemetryRequest request) {
        DeviceTelemetry telemetry = DeviceTelemetryRepository.findByDeviceCode(deviceCode)
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

        DeviceTelemetry saved = DeviceTelemetryRepository.save(telemetry);
        environmentalMeasurementService.recordAirPressure(device, deviceCode, request);
        eventPublisher.publishEvent(new DeviceTelemetrySavedEvent(saved.getId(), deviceCode));
        return saved;
    }

    private Device resolveRegisteredDevice(String deviceCode) {
        return deviceRepository.findBySerialNumber(deviceCode)
                .or(() -> deviceRepository.findById(deviceCode))
                .or(() -> droneRepository.findByDroneCode(deviceCode).map(drone -> drone.getDevice()))
                .orElseThrow(() -> new ApiException(ErrorCode.DEVICE_NOT_FOUND,
                        "Registered device not found: " + deviceCode));
    }
}
