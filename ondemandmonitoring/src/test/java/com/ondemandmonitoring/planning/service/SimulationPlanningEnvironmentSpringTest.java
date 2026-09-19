package com.ondemandmonitoring.planning.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.ondemandmonitoring.planning.dto.response.EnvironmentSample;
import com.ondemandmonitoring.zone.repository.ZoneRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
class SimulationPlanningEnvironmentSpringTest {

    @Autowired
    private PlanningEnvironment planningEnvironment;

    @MockitoBean
    private ZoneRepository zoneRepository;

    @Test
    void springCanCreatePlanningEnvironmentAndLoadPlanningMap() {
        when(zoneRepository.findAllByRestrictedTrueOrderByCodeAsc()).thenReturn(List.of());

        EnvironmentSample sample = planningEnvironment.sample(0.0, -280.0);

        assertThat(sample.insideWorldBounds()).isTrue();
        assertThat(sample.terrainElevationM()).isNotNull();
        assertThat(sample.surfaceElevationM()).isNotNull();
    }
}
