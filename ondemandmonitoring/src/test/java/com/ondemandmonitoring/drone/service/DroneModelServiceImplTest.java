package com.ondemandmonitoring.drone.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ondemandmonitoring.common.api.PageResponse;
import com.ondemandmonitoring.common.exception.ApiException;
import com.ondemandmonitoring.drone.domain.DroneModel;
import com.ondemandmonitoring.drone.dto.request.DroneModelCreateRequest;
import com.ondemandmonitoring.drone.dto.request.DroneModelUpdateRequest;
import com.ondemandmonitoring.drone.dto.response.DroneModelResponse;
import com.ondemandmonitoring.drone.mapper.DroneModelMapper;
import com.ondemandmonitoring.drone.repository.DroneModelRepository;
import com.ondemandmonitoring.drone.service.impl.DroneModelServiceImpl;
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
class DroneModelServiceImplTest {

    @Mock
    private DroneModelRepository droneModelRepository;

    @Mock
    private DroneModelMapper droneModelMapper;

    @InjectMocks
    private DroneModelServiceImpl droneModelService;

    @Test
    void create_success() {
        DroneModelCreateRequest request = DroneModelCreateRequest.builder()
                .modelCode("M300-RTK")
                .manufacturer("DJI")
                .category("Quadcopter")
                .maxFlightTimeMinutes(45.0)
                .maxTakeoffWeightKg(9.0)
                .maxFlightAltitudeMeters(7000.0)
                .maxWindResistanceMetersPerSecond(15.0)
                .ipRating("IP45")
                .specsMetadata("{}")
                .build();

        DroneModel entity = new DroneModel();
        entity.setModelCode("M300-RTK");
        entity.setManufacturer("DJI");
        entity.setCategory("Quadcopter");

        DroneModel savedEntity = new DroneModel();
        savedEntity.setId("uuid-123");
        savedEntity.setModelCode("M300-RTK");
        savedEntity.setManufacturer("DJI");
        savedEntity.setCategory("Quadcopter");

        DroneModelResponse response = DroneModelResponse.builder()
                .id("uuid-123")
                .modelCode("M300-RTK")
                .manufacturer("DJI")
                .category("Quadcopter")
                .build();

        when(droneModelMapper.toEntity(request)).thenReturn(entity);
        when(droneModelRepository.save(entity)).thenReturn(savedEntity);
        when(droneModelMapper.toResponse(savedEntity)).thenReturn(response);

        DroneModelResponse result = droneModelService.create(request);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo("uuid-123");
        assertThat(result.getModelCode()).isEqualTo("M300-RTK");
        assertThat(result.getManufacturer()).isEqualTo("DJI");
        verify(droneModelRepository).save(entity);
    }

    @Test
    void getById_success() {
        DroneModel entity = new DroneModel();
        entity.setId("uuid-123");

        DroneModelResponse response = DroneModelResponse.builder()
                .id("uuid-123")
                .modelCode("M300-RTK")
                .build();

        when(droneModelRepository.findById("uuid-123")).thenReturn(Optional.of(entity));
        when(droneModelMapper.toResponse(entity)).thenReturn(response);

        DroneModelResponse result = droneModelService.getById("uuid-123");

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo("uuid-123");
    }

    @Test
    void getById_notFound_throwsException() {
        when(droneModelRepository.findById("invalid-id")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> droneModelService.getById("invalid-id"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Drone model not found with id: invalid-id");
    }

    @Test
    void getAll_success() {
        Pageable pageable = PageRequest.of(0, 10);
        DroneModel entity = new DroneModel();
        entity.setId("uuid-123");

        Page<DroneModel> page = new PageImpl<>(List.of(entity), pageable, 1);
        DroneModelResponse response = DroneModelResponse.builder().id("uuid-123").build();

        when(droneModelRepository.searchDroneModels(null, null, pageable)).thenReturn(page);
        when(droneModelMapper.toResponse(entity)).thenReturn(response);

        PageResponse<DroneModelResponse> result = droneModelService.getAll(pageable, null, null);

        assertThat(result).isNotNull();
        assertThat(result.getItems()).hasSize(1);
        assertThat(result.getTotalItems()).isEqualTo(1);
    }

    @Test
    void update_success() {
        DroneModelUpdateRequest request = DroneModelUpdateRequest.builder()
                .modelCode("M300-RTK")
                .manufacturer("DJI")
                .category("Quadcopter")
                .maxFlightTimeMinutes(50.0)
                .build();

        DroneModel entity = new DroneModel();
        entity.setId("uuid-123");

        DroneModelResponse response = DroneModelResponse.builder()
                .id("uuid-123")
                .modelCode("M300-RTK")
                .maxFlightTimeMinutes(50.0)
                .build();

        when(droneModelRepository.findById("uuid-123")).thenReturn(Optional.of(entity));
        when(droneModelRepository.save(entity)).thenReturn(entity);
        when(droneModelMapper.toResponse(entity)).thenReturn(response);

        DroneModelResponse result = droneModelService.update("uuid-123", request);

        assertThat(result).isNotNull();
        verify(droneModelMapper).updateEntityFromRequest(request, entity);
        verify(droneModelRepository).save(entity);
    }

    @Test
    void delete_success() {
        DroneModel entity = new DroneModel();
        entity.setId("uuid-123");

        when(droneModelRepository.findById("uuid-123")).thenReturn(Optional.of(entity));

        droneModelService.delete("uuid-123");

        verify(droneModelRepository).delete(entity);
    }
}
