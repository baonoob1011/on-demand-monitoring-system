package com.ondemandmonitoring.device.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ondemandmonitoring.common.api.PageResponse;
import com.ondemandmonitoring.common.exception.ApiException;
import com.ondemandmonitoring.device.domain.DeviceModel;
import com.ondemandmonitoring.device.domain.DeviceType;
import com.ondemandmonitoring.device.dto.request.DeviceModelCreateRequest;
import com.ondemandmonitoring.device.dto.request.DeviceModelUpdateRequest;
import com.ondemandmonitoring.device.dto.response.DeviceModelResponse;
import com.ondemandmonitoring.device.dto.response.DeviceTypeResponse;
import com.ondemandmonitoring.device.mapper.DeviceModelMapper;
import com.ondemandmonitoring.device.repository.DeviceModelRepository;
import com.ondemandmonitoring.device.repository.DeviceTypeRepository;
import com.ondemandmonitoring.device.service.impl.DeviceModelServiceImpl;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
class DeviceModelServiceImplTest {

    @Mock
    private DeviceModelRepository deviceModelRepository;

    @Mock
    private DeviceTypeRepository deviceTypeRepository;

    @Mock
    private DeviceModelMapper deviceModelMapper;

    @InjectMocks
    private DeviceModelServiceImpl deviceModelService;

    @Test
    void create_success() {
        Map<String, Object> specs = Map.of("resolution", "4K", "fps", 60);

        DeviceModelCreateRequest request = DeviceModelCreateRequest.builder()
                .code("DM-01")
                .manufacturer("Sony")
                .modelName("Alpha 7")
                .specsMetadata(specs)
                .deviceTypeIds(Set.of("type-1"))
                .build();

        DeviceModel entity = new DeviceModel();
        entity.setCode("DM-01");
        entity.setManufacturer("Sony");
        entity.setModelName("Alpha 7");
        entity.setSpecsMetadata(specs);

        DeviceType deviceType = new DeviceType();
        deviceType.setId("type-1");
        deviceType.setCode("CAM");

        DeviceModel savedEntity = new DeviceModel();
        savedEntity.setId("model-123");
        savedEntity.setCode("DM-01");

        DeviceModelResponse response = DeviceModelResponse.builder()
                .id("model-123")
                .code("DM-01")
                .manufacturer("Sony")
                .modelName("Alpha 7")
                .specsMetadata(specs)
                .deviceTypes(Set.of(DeviceTypeResponse.builder().id("type-1").code("CAM").build()))
                .build();

        when(deviceModelRepository.existsByCode("DM-01")).thenReturn(false);
        when(deviceModelMapper.toEntity(request)).thenReturn(entity);
        when(deviceTypeRepository.findAllById(Set.of("type-1"))).thenReturn(List.of(deviceType));
        when(deviceModelRepository.save(entity)).thenReturn(savedEntity);
        when(deviceModelMapper.toResponse(savedEntity)).thenReturn(response);

        DeviceModelResponse result = deviceModelService.create(request);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo("model-123");
        assertThat(result.getCode()).isEqualTo("DM-01");
        assertThat(result.getSpecsMetadata()).containsEntry("resolution", "4K");
        assertThat(result.getDeviceTypes()).hasSize(1);
        verify(deviceModelRepository).save(entity);
    }

    @Test
    void create_duplicateCode_throwsException() {
        DeviceModelCreateRequest request = DeviceModelCreateRequest.builder()
                .code("DM-01")
                .modelName("Alpha 7")
                .build();

        when(deviceModelRepository.existsByCode("DM-01")).thenReturn(true);

        assertThatThrownBy(() -> deviceModelService.create(request))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Device model code already exists: DM-01");
    }

    @Test
    void getById_success() {
        DeviceModel entity = new DeviceModel();
        entity.setId("model-123");

        DeviceModelResponse response = DeviceModelResponse.builder()
                .id("model-123")
                .code("DM-01")
                .build();

        when(deviceModelRepository.findById("model-123")).thenReturn(Optional.of(entity));
        when(deviceModelMapper.toResponse(entity)).thenReturn(response);

        DeviceModelResponse result = deviceModelService.getById("model-123");

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo("model-123");
    }

    @Test
    void getById_notFound_throwsException() {
        when(deviceModelRepository.findById("invalid-id")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> deviceModelService.getById("invalid-id"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Device model not found with id: invalid-id");
    }

    @Test
    void getAll_success() {
        Pageable pageable = PageRequest.of(0, 10);
        DeviceModel entity = new DeviceModel();
        entity.setId("model-123");

        Page<DeviceModel> page = new PageImpl<>(List.of(entity), pageable, 1);
        DeviceModelResponse response = DeviceModelResponse.builder().id("model-123").build();

        when(deviceModelRepository.searchDeviceModels(null, null, pageable)).thenReturn(page);
        when(deviceModelMapper.toResponse(entity)).thenReturn(response);

        PageResponse<DeviceModelResponse> result = deviceModelService.getAll(pageable, null, null);

        assertThat(result).isNotNull();
        assertThat(result.getItems()).hasSize(1);
        assertThat(result.getTotalItems()).isEqualTo(1);
    }

    @Test
    void update_success() {
        Map<String, Object> updatedSpecs = Map.of("resolution", "8K");

        DeviceModelUpdateRequest request = DeviceModelUpdateRequest.builder()
                .code("DM-02")
                .modelName("Alpha 9")
                .specsMetadata(updatedSpecs)
                .deviceTypeIds(Set.of("type-1"))
                .build();

        DeviceModel entity = new DeviceModel();
        entity.setId("model-123");
        entity.setCode("DM-01");

        DeviceType deviceType = new DeviceType();
        deviceType.setId("type-1");

        DeviceModelResponse response = DeviceModelResponse.builder()
                .id("model-123")
                .code("DM-02")
                .modelName("Alpha 9")
                .specsMetadata(updatedSpecs)
                .build();

        when(deviceModelRepository.findById("model-123")).thenReturn(Optional.of(entity));
        when(deviceModelRepository.existsByCodeAndIdNot("DM-02", "model-123")).thenReturn(false);
        when(deviceTypeRepository.findAllById(Set.of("type-1"))).thenReturn(List.of(deviceType));
        when(deviceModelRepository.save(entity)).thenReturn(entity);
        when(deviceModelMapper.toResponse(entity)).thenReturn(response);

        DeviceModelResponse result = deviceModelService.update("model-123", request);

        assertThat(result).isNotNull();
        verify(deviceModelMapper).updateEntityFromRequest(request, entity);
        verify(deviceModelRepository).save(entity);
    }

    @Test
    void update_duplicateCode_throwsException() {
        DeviceModelUpdateRequest request = DeviceModelUpdateRequest.builder()
                .code("DM-02")
                .build();

        DeviceModel entity = new DeviceModel();
        entity.setId("model-123");

        when(deviceModelRepository.findById("model-123")).thenReturn(Optional.of(entity));
        when(deviceModelRepository.existsByCodeAndIdNot("DM-02", "model-123")).thenReturn(true);

        assertThatThrownBy(() -> deviceModelService.update("model-123", request))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Device model code already exists: DM-02");
    }

    @Test
    void delete_success() {
        DeviceModel entity = new DeviceModel();
        entity.setId("model-123");

        when(deviceModelRepository.findById("model-123")).thenReturn(Optional.of(entity));

        deviceModelService.delete("model-123");

        verify(deviceModelRepository).delete(entity);
    }
}
