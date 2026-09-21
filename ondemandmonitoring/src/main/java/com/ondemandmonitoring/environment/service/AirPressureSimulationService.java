package com.ondemandmonitoring.environment.service;

import com.ondemandmonitoring.environment.domain.AtmosphereProfile;
import com.ondemandmonitoring.environment.repository.AtmosphereProfileRepository;
import com.ondemandmonitoring.zone.config.SimulationZoneSeeder;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AirPressureSimulationService {

    static final double DEFAULT_BASE_PRESSURE_PA = 101_325.0;
    static final double DEFAULT_BASE_ALTITUDE_M = 0.0;
    static final double SEA_LEVEL_STANDARD_TEMPERATURE_K = 288.15;
    static final double TROPOSPHERE_LAPSE_RATE_K_PER_M = 0.0065;
    static final double BAROMETRIC_EXPONENT = 5.255877;

    AtmosphereProfileRepository atmosphereProfileRepository;

    @Transactional(readOnly = true)
    public double calculatePressurePa(double altitudeM) {
        AtmosphereProfile profile = atmosphereProfileRepository
                .findFirstBySourceWorldAndActiveTrueOrderByCreatedAtDesc(SimulationZoneSeeder.SOURCE_WORLD)
                .orElseGet(this::defaultProfile);
        return calculatePressurePa(altitudeM, profile);
    }

    public double calculatePressurePa(double altitudeM, AtmosphereProfile profile) {
        double basePressurePa = valueOrDefault(profile.getBasePressurePa(), DEFAULT_BASE_PRESSURE_PA);
        double baseAltitudeM = valueOrDefault(profile.getBaseAltitudeM(), DEFAULT_BASE_ALTITUDE_M);
        double altitudeDeltaM = altitudeM - baseAltitudeM;
        double ratio = 1.0 - (TROPOSPHERE_LAPSE_RATE_K_PER_M * altitudeDeltaM / SEA_LEVEL_STANDARD_TEMPERATURE_K);
        if (ratio <= 0.0) {
            throw new IllegalArgumentException("Altitude is outside the supported low-atmosphere simulation range");
        }
        return basePressurePa * Math.pow(ratio, BAROMETRIC_EXPONENT);
    }

    private AtmosphereProfile defaultProfile() {
        AtmosphereProfile profile = new AtmosphereProfile();
        profile.setSourceWorld(SimulationZoneSeeder.SOURCE_WORLD);
        profile.setBasePressurePa(DEFAULT_BASE_PRESSURE_PA);
        profile.setBaseAltitudeM(DEFAULT_BASE_ALTITUDE_M);
        profile.setActive(true);
        return profile;
    }

    private double valueOrDefault(Double value, double fallback) {
        return value == null ? fallback : value;
    }
}
