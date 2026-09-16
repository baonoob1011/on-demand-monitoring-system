package com.ondemandmonitoring.drone.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ondemandmonitoring.common.api.PageResponse;
import com.ondemandmonitoring.common.exception.ApiException;
import com.ondemandmonitoring.common.exception.ErrorCode;
import com.ondemandmonitoring.drone.domain.Drone;
import com.ondemandmonitoring.drone.domain.DroneModel;
import com.ondemandmonitoring.drone.domain.DronePayload;
import com.ondemandmonitoring.drone.dto.request.DroneCreateRequest;
import com.ondemandmonitoring.drone.dto.request.DroneUpdateRequest;
import com.ondemandmonitoring.drone.dto.response.DroneModelResponse;
import com.ondemandmonitoring.drone.dto.response.DronePayloadResponse;
import com.ondemandmonitoring.drone.dto.response.DroneResponse;
import com.ondemandmonitoring.drone.enums.DroneStatus;
import com.ondemandmonitoring.drone.mapper.DroneMapper;
import com.ondemandmonitoring.drone.repository.DroneModelRepository;
import com.ondemandmonitoring.drone.repository.DronePayloadRepository;
import com.ondemandmonitoring.drone.repository.DroneRepository;
import com.ondemandmonitoring.drone.service.impl.DroneServiceImpl;
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
class DroneServiceImplTest {

    @Mock
    private DroneRepository droneRepository;

    @Mock
    private DroneModelRepository droneModelRepository;

    @Mock
    private DronePayloadRepository dronePayloadRepository;

    @Mock
    private DroneMapper droneMapper;

    @InjectMocks
    private DroneServiceImpl droneService;

    @Test
    void create_success() {
        DroneCreateRequest request = DroneCreateRequest.builder()
                .serialNumber("SN-123456")
                .droneModelId("model-1")
                .dronePayloadId("payload-1")
                .status(DroneStatus.AVAILABLE)
                .build();

        DroneModel droneModel = new DroneModel();
        droneModel.setId("model-1");

        DronePayload dronePayload = new DronePayload();
        dronePayload.setId("payload-1");

        Drone entity = new Drone();
        entity.setSerialNumber("SN-123456");

        Drone savedEntity = new Drone();
        savedEntity.setId("drone-1");
        savedEntity.setSerialNumber("SN-123456");
        savedEntity.setDroneModel(droneModel);
        savedEntity.setDronePayload(dronePayload);
        savedEntity.setStatus(DroneStatus.AVAILABLE);

        DroneResponse response = DroneResponse.builder()
                .id("drone-1")
                .serialNumber("SN-123456")
                .droneModel(DroneModelResponse.builder().id("model-1").build())
                .dronePayload(DronePayloadResponse.builder().id("payload-1").build())
                .status(DroneStatus.AVAILABLE)
                .build();

        when(droneRepository.existsBySerialNumber("SN-123456")).thenReturn(false);
        when(droneModelRepository.findById("model-1")).thenReturn(Optional.of(droneModel));
        when(dronePayloadRepository.findById("payload-1")).thenReturn(Optional.of(dronePayload));
        when(droneMapper.toEntity(request)).thenReturn(entity);
        when(droneRepository.save(entity)).thenReturn(savedEntity);
        when(droneMapper.toResponse(savedEntity)).thenReturn(response);

        DroneResponse result = droneService.create(request);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo("drone-1");
        assertThat(result.getSerialNumber()).isEqualTo("SN-123456");
        verify(droneRepository).save(entity);
    }

    @Test
    void create_duplicateSerialNumber_throwsException() {
        DroneCreateRequest request = DroneCreateRequest.builder()
                .serialNumber("SN-EXISTS")
                .droneModelId("model-1")
                .status(DroneStatus.AVAILABLE)
                .build();

        when(droneRepository.existsBySerialNumber("SN-EXISTS")).thenReturn(true);

        assertThatThrownBy(() -> droneService.create(request))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Drone already exists with serial number: SN-EXISTS");
    }

    @Test
    void create_droneModelNotFound_throwsException() {
        DroneCreateRequest request = DroneCreateRequest.builder()
                .serialNumber("SN-123456")
                .droneModelId("invalid-model")
                .status(DroneStatus.AVAILABLE)
                .build();

        when(droneRepository.existsBySerialNumber("SN-123456")).thenReturn(false);
        when(droneModelRepository.findById("invalid-model")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> droneService.create(request))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Drone model not found with id: invalid-model");
    }

    @Test
    void create_dronePayloadNotFound_throwsException() {
        DroneCreateRequest request = DroneCreateRequest.builder()
                .serialNumber("SN-123456")
                .droneModelId("model-1")
                .dronePayloadId("invalid-payload")
                .status(DroneStatus.AVAILABLE)
                .build();

        DroneModel droneModel = new DroneModel();
        droneModel.setId("model-1");

        when(droneRepository.existsBySerialNumber("SN-123456")).thenReturn(false);
        when(droneModelRepository.findById("model-1")).thenReturn(Optional.of(droneModel));
        when(dronePayloadRepository.findById("invalid-payload")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> droneService.create(request))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Drone payload not found with id: invalid-payload");
    }

    @Test
    void getById_success() {
        Drone entity = new Drone();
        entity.setId("drone-1");

        DroneResponse response = DroneResponse.builder()
                .id("drone-1")
                .build();

        when(droneRepository.findById("drone-1")).thenReturn(Optional.of(entity));
        when(droneMapper.toResponse(entity)).thenReturn(response);

        DroneResponse result = droneService.getById("drone-1");

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo("drone-1");
    }

    @Test
    void getById_notFound_throwsException() {
        when(droneRepository.findById("invalid-id")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> droneService.getById("invalid-id"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Drone not found with id: invalid-id");
    }

    @Test
    void getAll_success() {
        Pageable pageable = PageRequest.of(0, 10);
        Drone entity = new Drone();
        entity.setId("drone-1");

        Page<Drone> page = new PageImpl<>(List.of(entity), pageable, 1);
        DroneResponse response = DroneResponse.builder().id("drone-1").build();

        when(droneRepository.searchDrones(null, null, null, pageable)).thenReturn(page);
        when(droneMapper.toResponse(entity)).thenReturn(response);

        PageResponse<DroneResponse> result = droneService.getAll(pageable, null, null, null);

        assertThat(result).isNotNull();
        assertThat(result.getItems()).hasSize(1);
        assertThat(result.getTotalItems()).isEqualTo(1);
    }

    @Test
    void update_success() {
        DroneUpdateRequest request = DroneUpdateRequest.builder()
                .serialNumber("SN-UPDATED")
                .droneModelId("model-1")
                .status(DroneStatus.MAINTENANCE)
                .build();

        Drone entity = new Drone();
        entity.setId("drone-1");

        DroneModel droneModel = new DroneModel();
        droneModel.setId("model-1");

        DroneResponse response = DroneResponse.builder()
                .id("drone-1")
                .serialNumber("SN-UPDATED")
                .status(DroneStatus.MAINTENANCE)
                .build();

        when(droneRepository.findById("drone-1")).thenReturn(Optional.of(entity));
        when(droneRepository.existsBySerialNumberAndIdNot("SN-UPDATED", "drone-1")).thenReturn(false);
        when(droneModelRepository.findById("model-1")).thenReturn(Optional.of(droneModel));
        when(droneRepository.save(entity)).thenReturn(entity);
        when(droneMapper.toResponse(entity)).thenReturn(response);

        DroneResponse result = droneService.update("drone-1", request);

        assertThat(result).isNotNull();
        verify(droneMapper).updateEntityFromRequest(request, entity);
        verify(droneRepository).save(entity);
    }

    @Test
    void delete_success() {
        Drone entity = new Drone();
        entity.setId("drone-1");

        when(droneRepository.findById("drone-1")).thenReturn(Optional.of(entity));

        droneService.delete("drone-1");

        verify(droneRepository).delete(entity);
    }
}
