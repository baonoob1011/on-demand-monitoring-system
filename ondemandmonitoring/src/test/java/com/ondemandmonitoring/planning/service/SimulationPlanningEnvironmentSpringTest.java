package com.ondemandmonitoring.planning.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

import com.ondemandmonitoring.planning.dto.response.EnvironmentSample;
import com.ondemandmonitoring.planning.service.impl.SimulationPlanningEnvironment;
import com.ondemandmonitoring.zone.repository.ZoneRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig(classes = {
        SimulationPlanningEnvironment.class,
        SimulationPlanningEnvironmentSpringTest.TestConfig.class
})
class SimulationPlanningEnvironmentSpringTest {

    @Autowired
    private PlanningEnvironment planningEnvironment;

    @Autowired
    private ZoneRepository zoneRepository;

    @BeforeEach
    void setUp() {
        reset(zoneRepository);
    }

    @Test
    void springCanCreatePlanningEnvironmentAndLoadPlanningMap() {
        when(zoneRepository.findAllByRestrictedTrueOrderByCodeAsc()).thenReturn(List.of());

        EnvironmentSample sample = planningEnvironment.sample(0.0, -280.0);

        assertThat(sample.insideWorldBounds()).isTrue();
        assertThat(sample.terrainElevationM()).isNotNull();
        assertThat(sample.surfaceElevationM()).isNotNull();
    }

    @Configuration
    static class TestConfig {

        @Bean
        ZoneRepository zoneRepository() {
            return mock(ZoneRepository.class);
        }
    }
}
