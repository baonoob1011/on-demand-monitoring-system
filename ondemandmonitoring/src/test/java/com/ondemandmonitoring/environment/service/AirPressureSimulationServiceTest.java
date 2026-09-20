package com.ondemandmonitoring.environment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ondemandmonitoring.environment.domain.AtmosphereProfile;
import com.ondemandmonitoring.environment.repository.AtmosphereProfileRepository;
import com.ondemandmonitoring.zone.config.SimulationZoneSeeder;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.assertj.core.data.Offset;

class AirPressureSimulationServiceTest {

    private AirPressureSimulationService service;

    @BeforeEach
    void setUp() {
        AtmosphereProfileRepository repository = mock(AtmosphereProfileRepository.class);
        AtmosphereProfile profile = new AtmosphereProfile();
        profile.setSourceWorld(SimulationZoneSeeder.SOURCE_WORLD);
        profile.setBasePressurePa(101_325.0);
        profile.setBaseAltitudeM(0.0);
        profile.setActive(true);

        when(repository.findFirstBySourceWorldAndActiveTrueOrderByCreatedAtDesc(SimulationZoneSeeder.SOURCE_WORLD))
                .thenReturn(Optional.of(profile));
        service = new AirPressureSimulationService(repository);
    }

    @Test
    void calculatesStandardAtmospherePressureForLowAltitudes() {
        assertThat(service.calculatePressurePa(0.0)).isCloseTo(101_325.0, Offset.offset(1.0));
        assertThat(service.calculatePressurePa(10.0)).isCloseTo(101_205.0, Offset.offset(2.0));
        assertThat(service.calculatePressurePa(50.0)).isCloseTo(100_725.0, Offset.offset(2.0));
        assertThat(service.calculatePressurePa(100.0)).isCloseTo(100_129.0, Offset.offset(2.0));
    }

    @Test
    void pressureDecreasesAsAltitudeIncreases() {
        double ground = service.calculatePressurePa(0.0);
        double tenMeters = service.calculatePressurePa(10.0);
        double fiftyMeters = service.calculatePressurePa(50.0);
        double hundredMeters = service.calculatePressurePa(100.0);

        assertThat(tenMeters).isLessThan(ground);
        assertThat(fiftyMeters).isLessThan(tenMeters);
        assertThat(hundredMeters).isLessThan(fiftyMeters);
    }
}
