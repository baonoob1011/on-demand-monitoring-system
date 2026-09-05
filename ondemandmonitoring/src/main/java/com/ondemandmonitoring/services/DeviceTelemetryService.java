package com.ondemandmonitoring.services;

import com.ondemandmonitoring.dto.request.TelemetryRequest;
import com.ondemandmonitoring.entities.DeviceTelemetry;
import com.ondemandmonitoring.repositories.DeviceTelemetryRepository;
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

    @Transactional
    public DeviceTelemetry save(String deviceCode, TelemetryRequest request) {
        DeviceTelemetry telemetry = new DeviceTelemetry();
        telemetry.setDeviceCode(deviceCode);
        telemetry.setLatitude(request.getLatitude());
        telemetry.setLongitude(request.getLongitude());
        telemetry.setAltitude(request.getAltitude());
        telemetry.setBatteryPercent(request.getBatteryPercent());
        telemetry.setSpeed(request.getSpeed());
        telemetry.setFlightMode(request.getFlightMode());
        telemetry.setArmed(request.getArmed());

        return deviceTelemetryRepository.save(telemetry);
    }
}
