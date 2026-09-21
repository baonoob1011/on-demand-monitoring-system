package com.ondemandmonitoring.environment.service;

import com.ondemandmonitoring.drone.domain.Drone;
import com.ondemandmonitoring.drone.dto.request.TelemetryRequest;
import com.ondemandmonitoring.environment.domain.EnvironmentalMeasurement;
import com.ondemandmonitoring.environment.enums.MeasurementType;
import com.ondemandmonitoring.environment.repository.EnvironmentalMeasurementRepository;
import com.ondemandmonitoring.zone.config.SimulationZoneSeeder;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class EnvironmentalMeasurementService {

    AirPressureSimulationService airPressureSimulationService;
    EnvironmentalMeasurementRepository environmentalMeasurementRepository;

    public EnvironmentalMeasurement recordAirPressure(Drone drone, String droneCode, TelemetryRequest request) {
        Double altitudeM = resolveAltitudeM(request);
        if (altitudeM == null) {
            return null;
        }

        double pressurePa = airPressureSimulationService.calculatePressurePa(altitudeM);
        EnvironmentalMeasurement measurement = new EnvironmentalMeasurement();
        measurement.setDrone(drone);
        measurement.setDroneCode(droneCode);
        measurement.setMeasurementType(MeasurementType.AIR_PRESSURE);
        measurement.setValue(pressurePa);
        measurement.setUnit("Pa");
        measurement.setAltitudeM(altitudeM);
        measurement.setSimX(request.getSimX());
        measurement.setSimY(request.getSimY());
        measurement.setSourceWorld(SimulationZoneSeeder.SOURCE_WORLD);
        measurement.setMeasuredAt(Instant.now());
        return environmentalMeasurementRepository.save(measurement);
    }

    private Double resolveAltitudeM(TelemetryRequest request) {
        if (request.getRelativeAltitude() != null) {
            return request.getRelativeAltitude();
        }
        return request.getAltitude();
    }
}
