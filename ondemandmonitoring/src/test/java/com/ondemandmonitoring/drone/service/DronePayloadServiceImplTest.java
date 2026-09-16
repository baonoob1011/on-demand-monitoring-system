package com.ondemandmonitoring.drone.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ondemandmonitoring.common.api.PageResponse;
import com.ondemandmonitoring.common.exception.ApiException;
import com.ondemandmonitoring.drone.domain.DronePayload;
import com.ondemandmonitoring.drone.dto.request.DronePayloadCreateRequest;
import com.ondemandmonitoring.drone.dto.request.DronePayloadUpdateRequest;
import com.ondemandmonitoring.drone.dto.response.DronePayloadResponse;
import com.ondemandmonitoring.drone.mapper.DronePayloadMapper;
import com.ondemandmonitoring.drone.repository.DronePayloadRepository;
import com.ondemandmonitoring.drone.service.impl.DronePayloadServiceImpl;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class DronePayloadServiceImplTest {

    @Mock
    private DronePayloadRepository dronePayloadRepository;

    @Mock
    private DronePayloadMapper dronePayloadMapper;

    @InjectMocks
    private DronePayloadServiceImpl dronePayloadService;

    @Test
    void create_success() {
        DronePayloadCreateRequest request = DronePayloadCreateRequest.builder()
                .modelName("Zenmuse H20T")
                .sensorType("THERMAL")
                .weightKg(0.82)
                .payloadCapabilities("{\"zoom\": \"20x\", \"thermalResolution\": \"640x512\"}")
                .build();

        DronePayload entity = new DronePayload();
        entity.setModelName("Zenmuse H20T");
        entity.setSensorType("THERMAL");

        DronePayload savedEntity = new DronePayload();
        savedEntity.setId("uuid-456");
        savedEntity.setModelName("Zenmuse H20T");
        savedEntity.setSensorType("THERMAL");

        DronePayloadResponse response = DronePayloadResponse.builder()
                .id("uuid-456")
                .modelName("Zenmuse H20T")
                .sensorType("THERMAL")
                .build();

        when(dronePayloadMapper.toEntity(request)).thenReturn(entity);
        when(dronePayloadRepository.save(entity)).thenReturn(savedEntity);
        when(dronePayloadMapper.toResponse(savedEntity)).thenReturn(response);

        DronePayloadResponse result = dronePayloadService.create(request);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo("uuid-456");
        assertThat(result.getModelName()).isEqualTo("Zenmuse H20T");
        verify(dronePayloadRepository).save(entity);
    }

    @Test
    void getById_success() {
        DronePayload entity = new DronePayload();
        entity.setId("uuid-456");

        DronePayloadResponse response = DronePayloadResponse.builder()
                .id("uuid-456")
                .modelName("Zenmuse H20T")
                .build();

        when(dronePayloadRepository.findById("uuid-456")).thenReturn(Optional.of(entity));
        when(dronePayloadMapper.toResponse(entity)).thenReturn(response);

        DronePayloadResponse result = dronePayloadService.getById("uuid-456");

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo("uuid-456");
    }

    @Test
    void getById_notFound_throwsException() {
        when(dronePayloadRepository.findById("invalid-id")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> dronePayloadService.getById("invalid-id"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Drone payload not found with id: invalid-id");
    }

    @Test
    void getAll_success() {
        Pageable pageable = PageRequest.of(0, 10);
        DronePayload entity = new DronePayload();
        entity.setId("uuid-456");

        Page<DronePayload> page = new PageImpl<>(List.of(entity), pageable, 1);
        DronePayloadResponse response = DronePayloadResponse.builder().id("uuid-456").build();

        when(dronePayloadRepository.searchDronePayloads(null, null, pageable)).thenReturn(page);
        when(dronePayloadMapper.toResponse(entity)).thenReturn(response);

        PageResponse<DronePayloadResponse> result = dronePayloadService.getAll(pageable, null, null);

        assertThat(result).isNotNull();
        assertThat(result.getItems()).hasSize(1);
        assertThat(result.getTotalItems()).isEqualTo(1);
    }

    @Test
    void update_success() {
        DronePayloadUpdateRequest request = DronePayloadUpdateRequest.builder()
                .modelName("Zenmuse H20T V2")
                .sensorType("THERMAL")
                .weightKg(0.85)
                .build();

        DronePayload entity = new DronePayload();
        entity.setId("uuid-456");

        DronePayloadResponse response = DronePayloadResponse.builder()
                .id("uuid-456")
                .modelName("Zenmuse H20T V2")
                .build();

        when(dronePayloadRepository.findById("uuid-456")).thenReturn(Optional.of(entity));
        when(dronePayloadRepository.save(entity)).thenReturn(entity);
        when(dronePayloadMapper.toResponse(entity)).thenReturn(response);

        DronePayloadResponse result = dronePayloadService.update("uuid-456", request);

        assertThat(result).isNotNull();
        verify(dronePayloadMapper).updateEntityFromRequest(request, entity);
        verify(dronePayloadRepository).save(entity);
    }

    @Test
    void delete_success() {
        DronePayload entity = new DronePayload();
        entity.setId("uuid-456");

        when(dronePayloadRepository.findById("uuid-456")).thenReturn(Optional.of(entity));

        dronePayloadService.delete("uuid-456");

        verify(dronePayloadRepository).delete(entity);
    }
}
