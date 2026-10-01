package com.ondemandmonitoring.device.service.impl;

import com.ondemandmonitoring.device.domain.Device;
import com.ondemandmonitoring.device.domain.DeviceTelemetry;
import com.ondemandmonitoring.device.dto.request.TelemetryRequest;
import com.ondemandmonitoring.device.repository.DeviceRepository;
import com.ondemandmonitoring.device.repository.DeviceTelemetryRepository;
import com.ondemandmonitoring.device.service.IDeviceTelemetryService;
import com.ondemandmonitoring.environment.service.EnvironmentalMeasurementService;
import com.ondemandmonitoring.mission.domain.DeviceConnection;
import com.ondemandmonitoring.mission.service.IDeviceConnectionService;
import com.ondemandmonitoring.replanning.event.DeviceTelemetrySavedEvent;
import java.time.Instant;
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
public class DeviceTelemetryServiceImpl implements IDeviceTelemetryService {

    IDeviceConnectionService deviceConnectionService;
    DeviceTelemetryRepository telemetryRepository;
    DeviceRepository deviceRepository;
    EnvironmentalMeasurementService environmentalMeasurementService;
    ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional
    public void record(String deviceId, TelemetryRequest request) {
        DeviceConnection connection = deviceConnectionService.requireActiveTelemetryConnection(
                deviceId,
                request.getMissionId());
        Device device = connection.getDevice();

        DeviceTelemetry telemetry = new DeviceTelemetry();
        telemetry.setDeviceConnection(connection);
        telemetry.setBatteryPercent(request.getBatteryPercent());
        telemetry.setSimX(request.getSimX());
        telemetry.setSimY(request.getSimY());
        telemetry.setConnected(request.getConnected());
        telemetry.setRecordedAt(Instant.now());
        DeviceTelemetry saved = telemetryRepository.save(telemetry);

        device.setLastSeenAt(LocalDateTime.now());
        deviceRepository.save(device);

        String deviceCode = device.getDeviceCode() == null || device.getDeviceCode().isBlank()
                ? device.getId()
                : device.getDeviceCode();
        environmentalMeasurementService.recordAirPressure(device, deviceCode, request);
        eventPublisher.publishEvent(new DeviceTelemetrySavedEvent(saved.getId(), deviceId));
    }
}
