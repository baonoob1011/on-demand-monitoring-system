package com.ondemandmonitoring.device.service;

import com.ondemandmonitoring.device.dto.request.TelemetryRequest;
import com.ondemandmonitoring.device.domain.Device;
import com.ondemandmonitoring.device.domain.DeviceTelemetry;
import com.ondemandmonitoring.device.enums.DeviceStatus;
import com.ondemandmonitoring.device.enums.DeviceType;
import com.ondemandmonitoring.device.repository.DeviceRepository;
import com.ondemandmonitoring.device.repository.DeviceTelemetryRepository;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class DeviceTelemetryService {

    DeviceTelemetryRepository deviceTelemetryRepository;
    DeviceRepository deviceRepository;

    @Transactional
    public DeviceTelemetry save(String deviceCode, TelemetryRequest request) {
        Device device = getOrCreateDrone(deviceCode);

        DeviceTelemetry telemetry = deviceTelemetryRepository.findByDeviceCode(deviceCode)
                .orElseGet(DeviceTelemetry::new);

        telemetry.setDeviceCode(deviceCode);
        telemetry.setDevice(device);
        telemetry.setLatitude(request.getLatitude());
        telemetry.setLongitude(request.getLongitude());
        telemetry.setAltitude(request.getAltitude());
        telemetry.setAbsoluteAltitude(request.getAbsoluteAltitude());
        telemetry.setRelativeAltitude(request.getRelativeAltitude());
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

        return deviceTelemetryRepository.save(telemetry);
    }

    private Device getOrCreateDrone(String deviceCode) {
        return deviceRepository.findByDeviceCode(deviceCode)
                .orElseGet(() -> {
                    Device device = new Device();
                    device.setDeviceCode(deviceCode);
                    device.setDeviceName("PX4 SITL Drone");
                    device.setDeviceType(DeviceType.DRONE);
                    device.setStatus(DeviceStatus.AVAILABLE);
                    device.setLastSeenAt(LocalDateTime.now());
                    return deviceRepository.save(device);
                });
    }
}
