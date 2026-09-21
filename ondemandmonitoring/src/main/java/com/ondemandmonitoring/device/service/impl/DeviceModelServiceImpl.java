package com.ondemandmonitoring.device.service.impl;

import com.ondemandmonitoring.common.api.PageResponse;
import com.ondemandmonitoring.common.exception.ApiException;
import com.ondemandmonitoring.common.exception.ErrorCode;
import com.ondemandmonitoring.device.domain.DeviceModel;
import com.ondemandmonitoring.device.domain.DeviceType;
import com.ondemandmonitoring.device.dto.request.DeviceModelCreateRequest;
import com.ondemandmonitoring.device.dto.request.DeviceModelUpdateRequest;
import com.ondemandmonitoring.device.dto.response.DeviceModelResponse;
import com.ondemandmonitoring.device.mapper.DeviceModelMapper;
import com.ondemandmonitoring.device.repository.DeviceModelRepository;
import com.ondemandmonitoring.device.repository.DeviceTypeRepository;
import com.ondemandmonitoring.device.service.IDeviceModelService;
import java.util.HashSet;
import java.util.List;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class DeviceModelServiceImpl implements IDeviceModelService {

    DeviceModelRepository deviceModelRepository;
    DeviceTypeRepository deviceTypeRepository;
    DeviceModelMapper deviceModelMapper;

    @Override
    @Transactional
    public DeviceModelResponse create(DeviceModelCreateRequest request) {
        if (deviceModelRepository.existsByCode(request.getCode())) {
            throw new ApiException(ErrorCode.DEVICE_MODEL_CODE_EXISTS,
                    "Device model code already exists: " + request.getCode());
        }

        DeviceModel deviceModel = deviceModelMapper.toEntity(request);

        if (request.getDeviceTypeIds() != null && !request.getDeviceTypeIds().isEmpty()) {
            List<DeviceType> deviceTypes = deviceTypeRepository.findAllById(request.getDeviceTypeIds());
            if (deviceTypes.size() != request.getDeviceTypeIds().size()) {
                throw new ApiException(ErrorCode.DEVICE_TYPE_NOT_FOUND, "One or more specified device types do not exist");
            }
            deviceModel.setDeviceTypes(new HashSet<>(deviceTypes));
        }

        DeviceModel saved = deviceModelRepository.save(deviceModel);
        return deviceModelMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public DeviceModelResponse getById(String id) {
        DeviceModel deviceModel = getEntityById(id);
        return deviceModelMapper.toResponse(deviceModel);
    }

    @Override
    @Transactional(readOnly = true)
    public DeviceModel getEntityById(String id) {
        return deviceModelRepository.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.DEVICE_MODEL_NOT_FOUND,
                        "Device model not found with id: " + id));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<DeviceModelResponse> getAll(Pageable pageable, String search, String deviceTypeId) {
        Page<DeviceModel> page = deviceModelRepository.searchDeviceModels(search, deviceTypeId, pageable);
        Page<DeviceModelResponse> mappedPage = page.map(deviceModelMapper::toResponse);
        return PageResponse.from(mappedPage);
    }

    @Override
    @Transactional
    public DeviceModelResponse update(String id, DeviceModelUpdateRequest request) {
        DeviceModel deviceModel = getEntityById(id);

        if (request.getCode() != null && deviceModelRepository.existsByCodeAndIdNot(request.getCode(), id)) {
            throw new ApiException(ErrorCode.DEVICE_MODEL_CODE_EXISTS,
                    "Device model code already exists: " + request.getCode());
        }

        deviceModelMapper.updateEntityFromRequest(request, deviceModel);

        if (request.getDeviceTypeIds() != null) {
            List<DeviceType> deviceTypes = deviceTypeRepository.findAllById(request.getDeviceTypeIds());
            deviceModel.setDeviceTypes(new HashSet<>(deviceTypes));
        }

        DeviceModel saved = deviceModelRepository.save(deviceModel);
        return deviceModelMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public void delete(String id) {
        DeviceModel deviceModel = getEntityById(id);
        deviceModelRepository.delete(deviceModel);
    }
}
