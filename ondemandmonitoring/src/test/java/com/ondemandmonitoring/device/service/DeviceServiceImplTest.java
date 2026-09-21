package com.ondemandmonitoring.device.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ondemandmonitoring.common.api.PageResponse;
import com.ondemandmonitoring.common.exception.ApiException;
import com.ondemandmonitoring.device.domain.Device;
import com.ondemandmonitoring.device.domain.DeviceModel;
import com.ondemandmonitoring.device.dto.request.DeviceCreateRequest;
import com.ondemandmonitoring.device.dto.request.DeviceUpdateRequest;
import com.ondemandmonitoring.device.dto.response.DeviceModelResponse;
import com.ondemandmonitoring.device.dto.response.DeviceResponse;
import com.ondemandmonitoring.device.enums.DeviceStatus;
import com.ondemandmonitoring.device.mapper.DeviceMapper;
import com.ondemandmonitoring.device.repository.DeviceModelRepository;
import com.ondemandmonitoring.device.repository.DeviceRepository;
import com.ondemandmonitoring.device.service.impl.DeviceServiceImpl;
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
class DeviceServiceImplTest {

    @Mock
    private DeviceRepository deviceRepository;

    @Mock
    private DeviceModelRepository deviceModelRepository;

    @Mock
    private DeviceMapper deviceMapper;

    @InjectMocks
    private DeviceServiceImpl deviceService;

    @Test
    void create_success() {
        DeviceCreateRequest request = DeviceCreateRequest.builder()
                .serialNumber("SN-1001")
                .name("Camera Device 1")
                .modelId("model-123")
                .status(DeviceStatus.AVAILABLE)
                .build();

        DeviceModel model = new DeviceModel();
        model.setId("model-123");

        Device entity = new Device();
        entity.setSerialNumber("SN-1001");
        entity.setName("Camera Device 1");

        Device savedEntity = new Device();
        savedEntity.setId("device-123");
        savedEntity.setSerialNumber("SN-1001");
        savedEntity.setDeviceModel(model);
        savedEntity.setStatus(DeviceStatus.AVAILABLE);

        DeviceResponse response = DeviceResponse.builder()
                .id("device-123")
                .serialNumber("SN-1001")
                .name("Camera Device 1")
                .deviceModel(DeviceModelResponse.builder().id("model-123").build())
                .status(DeviceStatus.AVAILABLE)
                .build();

        when(deviceRepository.existsBySerialNumber("SN-1001")).thenReturn(false);
        when(deviceModelRepository.findById("model-123")).thenReturn(Optional.of(model));
        when(deviceMapper.toEntity(request)).thenReturn(entity);
        when(deviceRepository.save(entity)).thenReturn(savedEntity);
        when(deviceMapper.toResponse(savedEntity)).thenReturn(response);

        DeviceResponse result = deviceService.create(request);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo("device-123");
        assertThat(result.getSerialNumber()).isEqualTo("SN-1001");
        assertThat(result.getStatus()).isEqualTo(DeviceStatus.AVAILABLE);
        verify(deviceRepository).save(entity);
    }

    @Test
    void create_duplicateSerialNumber_throwsException() {
        DeviceCreateRequest request = DeviceCreateRequest.builder()
                .serialNumber("SN-1001")
                .modelId("model-123")
                .build();

        when(deviceRepository.existsBySerialNumber("SN-1001")).thenReturn(true);

        assertThatThrownBy(() -> deviceService.create(request))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Device serial number already exists: SN-1001");
    }

    @Test
    void create_modelNotFound_throwsException() {
        DeviceCreateRequest request = DeviceCreateRequest.builder()
                .serialNumber("SN-1001")
                .modelId("invalid-model-id")
                .build();

        when(deviceRepository.existsBySerialNumber("SN-1001")).thenReturn(false);
        when(deviceModelRepository.findById("invalid-model-id")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> deviceService.create(request))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Device model not found with id: invalid-model-id");
    }

    @Test
    void getById_success() {
        Device entity = new Device();
        entity.setId("device-123");

        DeviceResponse response = DeviceResponse.builder()
                .id("device-123")
                .serialNumber("SN-1001")
                .build();

        when(deviceRepository.findById("device-123")).thenReturn(Optional.of(entity));
        when(deviceMapper.toResponse(entity)).thenReturn(response);

        DeviceResponse result = deviceService.getById("device-123");

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo("device-123");
    }

    @Test
    void getById_notFound_throwsException() {
        when(deviceRepository.findById("invalid-id")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> deviceService.getById("invalid-id"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Device not found with id: invalid-id");
    }

    @Test
    void getAll_success() {
        Pageable pageable = PageRequest.of(0, 10);
        Device entity = new Device();
        entity.setId("device-123");

        Page<Device> page = new PageImpl<>(List.of(entity), pageable, 1);
        DeviceResponse response = DeviceResponse.builder().id("device-123").build();

        when(deviceRepository.findAll(pageable)).thenReturn(page);
        when(deviceMapper.toResponse(entity)).thenReturn(response);

        PageResponse<DeviceResponse> result = deviceService.getAll(pageable);

        assertThat(result).isNotNull();
        assertThat(result.getItems()).hasSize(1);
        assertThat(result.getTotalItems()).isEqualTo(1);
    }

    @Test
    void update_success() {
        DeviceUpdateRequest request = DeviceUpdateRequest.builder()
                .serialNumber("SN-1002")
                .status(DeviceStatus.IN_USE)
                .build();

        Device entity = new Device();
        entity.setId("device-123");
        entity.setSerialNumber("SN-1001");

        DeviceResponse response = DeviceResponse.builder()
                .id("device-123")
                .serialNumber("SN-1002")
                .status(DeviceStatus.IN_USE)
                .build();

        when(deviceRepository.findById("device-123")).thenReturn(Optional.of(entity));
        when(deviceRepository.existsBySerialNumberAndIdNot("SN-1002", "device-123")).thenReturn(false);
        when(deviceRepository.save(entity)).thenReturn(entity);
        when(deviceMapper.toResponse(entity)).thenReturn(response);

        DeviceResponse result = deviceService.update("device-123", request);

        assertThat(result).isNotNull();
        verify(deviceMapper).updateEntityFromRequest(request, entity);
        verify(deviceRepository).save(entity);
    }

    @Test
    void delete_success() {
        Device entity = new Device();
        entity.setId("device-123");

        when(deviceRepository.findById("device-123")).thenReturn(Optional.of(entity));

        deviceService.delete("device-123");

        verify(deviceRepository).delete(entity);
    }
}
