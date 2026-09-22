package com.ondemandmonitoring.device.service.impl;

import com.ondemandmonitoring.common.api.PageResponse;
import com.ondemandmonitoring.common.exception.ApiException;
import com.ondemandmonitoring.common.exception.ErrorCode;
import com.ondemandmonitoring.device.domain.DeviceType;
import com.ondemandmonitoring.device.dto.request.DeviceTypeCreateRequest;
import com.ondemandmonitoring.device.dto.request.DeviceTypeUpdateRequest;
import com.ondemandmonitoring.device.dto.response.DeviceTypeResponse;
import com.ondemandmonitoring.device.mapper.DeviceTypeMapper;
import com.ondemandmonitoring.device.repository.DeviceTypeRepository;
import com.ondemandmonitoring.device.service.IDeviceTypeService;
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
public class DeviceTypeServiceImpl implements IDeviceTypeService {

    DeviceTypeRepository deviceTypeRepository;
    DeviceTypeMapper deviceTypeMapper;

    @Override
    @Transactional
    public DeviceTypeResponse create(DeviceTypeCreateRequest request) {
        if (deviceTypeRepository.existsByCode(request.getCode())) {
            throw new ApiException(ErrorCode.DEVICE_TYPE_CODE_EXISTS,
                    "Device type code already exists: " + request.getCode());
        }
        DeviceType deviceType = deviceTypeMapper.toEntity(request);
        DeviceType saved = deviceTypeRepository.save(deviceType);
        return deviceTypeMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public DeviceTypeResponse getById(String id) {
        DeviceType deviceType = getEntityById(id);
        return deviceTypeMapper.toResponse(deviceType);
    }

    @Override
    @Transactional(readOnly = true)
    public DeviceType getEntityById(String id) {
        return deviceTypeRepository.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.DEVICE_TYPE_NOT_FOUND,
                        "Device type not found with id: " + id));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<DeviceTypeResponse> getAll(Pageable pageable) {
        Page<DeviceType> page = deviceTypeRepository.findAll(pageable);
        Page<DeviceTypeResponse> mappedPage = page.map(deviceTypeMapper::toResponse);
        return PageResponse.from(mappedPage);
    }

    @Override
    @Transactional
    public DeviceTypeResponse update(String id, DeviceTypeUpdateRequest request) {
        DeviceType deviceType = getEntityById(id);
        if (request.getCode() != null && deviceTypeRepository.existsByCodeAndIdNot(request.getCode(), id)) {
            throw new ApiException(ErrorCode.DEVICE_TYPE_CODE_EXISTS,
                    "Device type code already exists: " + request.getCode());
        }
        deviceTypeMapper.updateEntityFromRequest(request, deviceType);
        DeviceType saved = deviceTypeRepository.save(deviceType);
        return deviceTypeMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public void delete(String id) {
        DeviceType deviceType = getEntityById(id);
        deviceTypeRepository.delete(deviceType);
    }
}
