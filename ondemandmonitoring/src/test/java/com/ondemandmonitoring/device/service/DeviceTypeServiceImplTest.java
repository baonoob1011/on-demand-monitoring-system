package com.ondemandmonitoring.device.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ondemandmonitoring.common.api.PageResponse;
import com.ondemandmonitoring.common.exception.ApiException;
import com.ondemandmonitoring.device.domain.DeviceType;
import com.ondemandmonitoring.device.dto.request.DeviceTypeCreateRequest;
import com.ondemandmonitoring.device.dto.request.DeviceTypeUpdateRequest;
import com.ondemandmonitoring.device.dto.response.DeviceTypeResponse;
import com.ondemandmonitoring.device.mapper.DeviceTypeMapper;
import com.ondemandmonitoring.device.repository.DeviceTypeRepository;
import com.ondemandmonitoring.device.service.impl.DeviceTypeServiceImpl;
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
class DeviceTypeServiceImplTest {

    @Mock
    private DeviceTypeRepository deviceTypeRepository;

    @Mock
    private DeviceTypeMapper deviceTypeMapper;

    @InjectMocks
    private DeviceTypeServiceImpl deviceTypeService;

    @Test
    void create_success() {
        DeviceTypeCreateRequest request = DeviceTypeCreateRequest.builder()
                .code("CAM-01")
                .name("Camera Device")
                .description("High resolution camera sensor")
                .build();

        DeviceType entity = new DeviceType();
        entity.setCode("CAM-01");
        entity.setName("Camera Device");
        entity.setDescription("High resolution camera sensor");

        DeviceType savedEntity = new DeviceType();
        savedEntity.setId("uuid-123");
        savedEntity.setCode("CAM-01");
        savedEntity.setName("Camera Device");
        savedEntity.setDescription("High resolution camera sensor");

        DeviceTypeResponse response = DeviceTypeResponse.builder()
                .id("uuid-123")
                .code("CAM-01")
                .name("Camera Device")
                .description("High resolution camera sensor")
                .build();

        when(deviceTypeRepository.existsByCode("CAM-01")).thenReturn(false);
        when(deviceTypeMapper.toEntity(request)).thenReturn(entity);
        when(deviceTypeRepository.save(entity)).thenReturn(savedEntity);
        when(deviceTypeMapper.toResponse(savedEntity)).thenReturn(response);

        DeviceTypeResponse result = deviceTypeService.create(request);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo("uuid-123");
        assertThat(result.getCode()).isEqualTo("CAM-01");
        assertThat(result.getName()).isEqualTo("Camera Device");
        verify(deviceTypeRepository).save(entity);
    }

    @Test
    void create_duplicateCode_throwsException() {
        DeviceTypeCreateRequest request = DeviceTypeCreateRequest.builder()
                .code("CAM-01")
                .name("Camera Device")
                .build();

        when(deviceTypeRepository.existsByCode("CAM-01")).thenReturn(true);

        assertThatThrownBy(() -> deviceTypeService.create(request))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Device type code already exists: CAM-01");
    }

    @Test
    void getById_success() {
        DeviceType entity = new DeviceType();
        entity.setId("uuid-123");
        entity.setCode("CAM-01");

        DeviceTypeResponse response = DeviceTypeResponse.builder()
                .id("uuid-123")
                .code("CAM-01")
                .build();

        when(deviceTypeRepository.findById("uuid-123")).thenReturn(Optional.of(entity));
        when(deviceTypeMapper.toResponse(entity)).thenReturn(response);

        DeviceTypeResponse result = deviceTypeService.getById("uuid-123");

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo("uuid-123");
    }

    @Test
    void getById_notFound_throwsException() {
        when(deviceTypeRepository.findById("invalid-id")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> deviceTypeService.getById("invalid-id"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Device type not found with id: invalid-id");
    }

    @Test
    void getAll_success() {
        Pageable pageable = PageRequest.of(0, 10);
        DeviceType entity = new DeviceType();
        entity.setId("uuid-123");

        Page<DeviceType> page = new PageImpl<>(List.of(entity), pageable, 1);
        DeviceTypeResponse response = DeviceTypeResponse.builder().id("uuid-123").build();

        when(deviceTypeRepository.searchDeviceTypes(null, pageable)).thenReturn(page);
        when(deviceTypeMapper.toResponse(entity)).thenReturn(response);

        PageResponse<DeviceTypeResponse> result = deviceTypeService.getAll(pageable, null);

        assertThat(result).isNotNull();
        assertThat(result.getItems()).hasSize(1);
        assertThat(result.getTotalItems()).isEqualTo(1);
    }

    @Test
    void update_success() {
        DeviceTypeUpdateRequest request = DeviceTypeUpdateRequest.builder()
                .code("CAM-02")
                .name("Thermal Camera")
                .build();

        DeviceType entity = new DeviceType();
        entity.setId("uuid-123");
        entity.setCode("CAM-01");

        DeviceTypeResponse response = DeviceTypeResponse.builder()
                .id("uuid-123")
                .code("CAM-02")
                .name("Thermal Camera")
                .build();

        when(deviceTypeRepository.findById("uuid-123")).thenReturn(Optional.of(entity));
        when(deviceTypeRepository.existsByCodeAndIdNot("CAM-02", "uuid-123")).thenReturn(false);
        when(deviceTypeRepository.save(entity)).thenReturn(entity);
        when(deviceTypeMapper.toResponse(entity)).thenReturn(response);

        DeviceTypeResponse result = deviceTypeService.update("uuid-123", request);

        assertThat(result).isNotNull();
        verify(deviceTypeMapper).updateEntityFromRequest(request, entity);
        verify(deviceTypeRepository).save(entity);
    }

    @Test
    void update_duplicateCode_throwsException() {
        DeviceTypeUpdateRequest request = DeviceTypeUpdateRequest.builder()
                .code("CAM-02")
                .build();

        DeviceType entity = new DeviceType();
        entity.setId("uuid-123");

        when(deviceTypeRepository.findById("uuid-123")).thenReturn(Optional.of(entity));
        when(deviceTypeRepository.existsByCodeAndIdNot("CAM-02", "uuid-123")).thenReturn(true);

        assertThatThrownBy(() -> deviceTypeService.update("uuid-123", request))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Device type code already exists: CAM-02");
    }

    @Test
    void delete_success() {
        DeviceType entity = new DeviceType();
        entity.setId("uuid-123");

        when(deviceTypeRepository.findById("uuid-123")).thenReturn(Optional.of(entity));

        deviceTypeService.delete("uuid-123");

        verify(deviceTypeRepository).delete(entity);
    }
}
