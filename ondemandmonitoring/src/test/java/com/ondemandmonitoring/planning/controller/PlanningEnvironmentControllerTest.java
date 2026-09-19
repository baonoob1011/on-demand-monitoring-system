package com.ondemandmonitoring.planning.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ondemandmonitoring.planning.domain.PlanningGrid;
import com.ondemandmonitoring.planning.dto.response.EnvironmentSample;
import com.ondemandmonitoring.planning.service.PlanningEnvironment;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class PlanningEnvironmentControllerTest {

    private PlanningEnvironment planningEnvironment;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        planningEnvironment = mock(PlanningEnvironment.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new PlanningEnvironmentController(planningEnvironment)).build();
    }

    @Test
    void sampleDelegatesToPlanningEnvironment() throws Exception {
        when(planningEnvironment.sample(235.0, -42.0)).thenReturn(new EnvironmentSample(
                235.0,
                -42.0,
                true,
                9.874,
                35.498,
                45.372,
                false,
                null,
                null));

        mockMvc.perform(get("/api/planning/environment/sample")
                        .param("x", "235")
                        .param("y", "-42"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.simX").value(235.0))
                .andExpect(jsonPath("$.data.simY").value(-42.0))
                .andExpect(jsonPath("$.data.insideWorldBounds").value(true))
                .andExpect(jsonPath("$.data.terrainElevationM").value(9.874))
                .andExpect(jsonPath("$.data.obstacleHeightM").value(35.498))
                .andExpect(jsonPath("$.data.surfaceElevationM").value(45.372))
                .andExpect(jsonPath("$.data.restricted").value(false));

        verify(planningEnvironment).sample(235.0, -42.0);
    }

    @Test
    void metadataReturnsLoadedGridShape() throws Exception {
        when(planningEnvironment.grid()).thenReturn(grid());

        mockMvc.perform(get("/api/planning/environment/metadata"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.world").value("forest_monitoring_compact"))
                .andExpect(jsonPath("$.data.coordinateSystem").value("LOCAL_SIMULATION_METERS_GAZEBO_XY"))
                .andExpect(jsonPath("$.data.minX").value(-452.0))
                .andExpect(jsonPath("$.data.maxX").value(450.0))
                .andExpect(jsonPath("$.data.resolutionM").value(5.0))
                .andExpect(jsonPath("$.data.width").value(182))
                .andExpect(jsonPath("$.data.height").value(222));
    }

    @Test
    void gridReturnsDebugDatasetForSingleLoadFrontendOverlay() throws Exception {
        when(planningEnvironment.grid()).thenReturn(grid());

        mockMvc.perform(get("/api/planning/environment/grid"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.world").value("forest_monitoring_compact"))
                .andExpect(jsonPath("$.data.terrainElevationM", hasSize(4)))
                .andExpect(jsonPath("$.data.surfaceElevationM", hasSize(4)));
    }

    private PlanningGrid grid() {
        return new PlanningGrid(
                "forest_monitoring_compact",
                "LOCAL_SIMULATION_METERS_GAZEBO_XY",
                new PlanningGrid.Bounds(-452.0, 450.0, -415.0, 686.497),
                5.0,
                182,
                222,
                "nearest-cell",
                List.of(1.0, 2.0, 3.0, 4.0),
                List.of(0.0, 0.0, 1.0, 2.0),
                List.of(1.0, 2.0, 4.0, 6.0));
    }
}
