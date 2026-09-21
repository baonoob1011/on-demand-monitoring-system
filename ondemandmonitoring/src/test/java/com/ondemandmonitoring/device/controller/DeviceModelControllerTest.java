package com.ondemandmonitoring.device.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ondemandmonitoring.common.api.PageResponse;
import com.ondemandmonitoring.device.dto.response.DeviceModelResponse;
import com.ondemandmonitoring.device.service.IDeviceModelService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class DeviceModelControllerTest {

    private IDeviceModelService deviceModelService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        deviceModelService = mock(IDeviceModelService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new DeviceModelController(deviceModelService))
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .build();
    }

    @Test
    void create_returns201Created() throws Exception {
        Map<String, Object> specs = Map.of("resolution", "4K");

        DeviceModelResponse response = DeviceModelResponse.builder()
                .id("model-123")
                .code("DM-01")
                .manufacturer("Sony")
                .modelName("Alpha 7")
                .specsMetadata(specs)
                .build();

        when(deviceModelService.create(any())).thenReturn(response);

        mockMvc.perform(post("/api/device-models")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code": "DM-01",
                                  "manufacturer": "Sony",
                                  "modelName": "Alpha 7",
                                  "deviceTypeIds": ["type-1"],
                                  "specsMetadata": {
                                    "resolution": "4K"
                                  }
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Device model created successfully"))
                .andExpect(jsonPath("$.data.id").value("model-123"))
                .andExpect(jsonPath("$.data.code").value("DM-01"))
                .andExpect(jsonPath("$.data.modelName").value("Alpha 7"))
                .andExpect(jsonPath("$.data.specsMetadata.resolution").value("4K"));
    }

    @Test
    void create_blankCode_returns400() throws Exception {
        mockMvc.perform(post("/api/device-models")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code": "",
                                  "modelName": "Alpha 7"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getById_returns200() throws Exception {
        DeviceModelResponse response = DeviceModelResponse.builder()
                .id("model-123")
                .code("DM-01")
                .modelName("Alpha 7")
                .build();

        when(deviceModelService.getById("model-123")).thenReturn(response);

        mockMvc.perform(get("/api/device-models/model-123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value("model-123"))
                .andExpect(jsonPath("$.data.code").value("DM-01"));
    }

    @Test
    void getAll_returns200() throws Exception {
        DeviceModelResponse response = DeviceModelResponse.builder()
                .id("model-123")
                .code("DM-01")
                .modelName("Alpha 7")
                .build();

        PageResponse<DeviceModelResponse> pageResponse = PageResponse.from(
                new PageImpl<>(List.of(response), PageRequest.of(0, 10), 1)
        );

        when(deviceModelService.getAll(any(), any(), any())).thenReturn(pageResponse);

        mockMvc.perform(get("/api/device-models?search=Alpha&type=Camera"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.items[0].id").value("model-123"))
                .andExpect(jsonPath("$.data.items[0].code").value("DM-01"));
    }

    @Test
    void update_returns200() throws Exception {
        DeviceModelResponse response = DeviceModelResponse.builder()
                .id("model-123")
                .code("DM-02")
                .modelName("Alpha 9")
                .build();

        when(deviceModelService.update(eq("model-123"), any())).thenReturn(response);

        mockMvc.perform(put("/api/device-models/model-123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code": "DM-02",
                                  "modelName": "Alpha 9"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Device model updated successfully"))
                .andExpect(jsonPath("$.data.code").value("DM-02"));
    }

    @Test
    void delete_returns200() throws Exception {
        doNothing().when(deviceModelService).delete("model-123");

        mockMvc.perform(delete("/api/device-models/model-123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Device model deleted successfully"));

        verify(deviceModelService).delete("model-123");
    }
}
