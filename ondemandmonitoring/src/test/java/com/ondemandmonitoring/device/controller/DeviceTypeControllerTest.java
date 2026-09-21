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
import com.ondemandmonitoring.device.dto.response.DeviceTypeResponse;
import com.ondemandmonitoring.device.service.IDeviceTypeService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class DeviceTypeControllerTest {

    private IDeviceTypeService deviceTypeService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        deviceTypeService = mock(IDeviceTypeService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new DeviceTypeController(deviceTypeService))
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .build();
    }

    @Test
    void create_returns201Created() throws Exception {
        DeviceTypeResponse response = DeviceTypeResponse.builder()
                .id("uuid-123")
                .code("CAM-01")
                .name("Camera Device")
                .description("Camera device description")
                .build();

        when(deviceTypeService.create(any())).thenReturn(response);

        mockMvc.perform(post("/api/device-types")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code": "CAM-01",
                                  "name": "Camera Device",
                                  "description": "Camera device description"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Device type created successfully"))
                .andExpect(jsonPath("$.data.id").value("uuid-123"))
                .andExpect(jsonPath("$.data.code").value("CAM-01"))
                .andExpect(jsonPath("$.data.name").value("Camera Device"))
                .andExpect(jsonPath("$.data.description").value("Camera device description"));
    }

    @Test
    void create_blankCode_returns400() throws Exception {
        mockMvc.perform(post("/api/device-types")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code": "",
                                  "name": "Camera Device"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getById_returns200() throws Exception {
        DeviceTypeResponse response = DeviceTypeResponse.builder()
                .id("uuid-123")
                .code("CAM-01")
                .name("Camera Device")
                .build();

        when(deviceTypeService.getById("uuid-123")).thenReturn(response);

        mockMvc.perform(get("/api/device-types/uuid-123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value("uuid-123"))
                .andExpect(jsonPath("$.data.code").value("CAM-01"));
    }

    @Test
    void getAll_returns200() throws Exception {
        DeviceTypeResponse response = DeviceTypeResponse.builder()
                .id("uuid-123")
                .code("CAM-01")
                .name("Camera Device")
                .build();

        PageResponse<DeviceTypeResponse> pageResponse = PageResponse.from(
                new PageImpl<>(List.of(response), PageRequest.of(0, 10), 1)
        );

        when(deviceTypeService.getAll(any(), any())).thenReturn(pageResponse);

        mockMvc.perform(get("/api/device-types?search=CAM"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.items[0].id").value("uuid-123"))
                .andExpect(jsonPath("$.data.items[0].code").value("CAM-01"));
    }

    @Test
    void update_returns200() throws Exception {
        DeviceTypeResponse response = DeviceTypeResponse.builder()
                .id("uuid-123")
                .code("CAM-02")
                .name("Updated Camera")
                .build();

        when(deviceTypeService.update(eq("uuid-123"), any())).thenReturn(response);

        mockMvc.perform(put("/api/device-types/uuid-123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code": "CAM-02",
                                  "name": "Updated Camera"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Device type updated successfully"))
                .andExpect(jsonPath("$.data.code").value("CAM-02"));
    }

    @Test
    void delete_returns200() throws Exception {
        doNothing().when(deviceTypeService).delete("uuid-123");

        mockMvc.perform(delete("/api/device-types/uuid-123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Device type deleted successfully"));

        verify(deviceTypeService).delete("uuid-123");
    }
}
