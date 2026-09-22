package com.ondemandmonitoring.device.service.impl;

import com.ondemandmonitoring.common.api.PageResponse;
import com.ondemandmonitoring.common.exception.ApiException;
import com.ondemandmonitoring.common.exception.ErrorCode;
import com.ondemandmonitoring.device.domain.Device;
import com.ondemandmonitoring.device.domain.DeviceModel;
import com.ondemandmonitoring.device.dto.request.DeviceCreateRequest;
import com.ondemandmonitoring.device.dto.request.DeviceUpdateRequest;
import com.ondemandmonitoring.device.dto.response.DeviceResponse;
import com.ondemandmonitoring.device.enums.DeviceStatus;
import com.ondemandmonitoring.device.mapper.DeviceMapper;
import com.ondemandmonitoring.device.repository.DeviceModelRepository;
import com.ondemandmonitoring.device.repository.DeviceRepository;
import com.ondemandmonitoring.device.service.IDeviceService;
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
public class DeviceServiceImpl implements IDeviceService {

    DeviceRepository deviceRepository;
    DeviceModelRepository deviceModelRepository;
    DeviceMapper deviceMapper;

    @Override
    @Transactional
    public DeviceResponse create(DeviceCreateRequest request) {
        if (deviceRepository.existsBySerialNumber(request.getSerialNumber())) {
            throw new ApiException(ErrorCode.DEVICE_SERIAL_NUMBER_EXISTS,
                    "Device serial number already exists: " + request.getSerialNumber());
        }

        DeviceModel deviceModel = deviceModelRepository.findById(request.getModelId())
                .orElseThrow(() -> new ApiException(ErrorCode.DEVICE_MODEL_NOT_FOUND,
                        "Device model not found with id: " + request.getModelId()));

        Device device = deviceMapper.toEntity(request);
        device.setDeviceModel(deviceModel);

        if (device.getStatus() == null) {
            device.setStatus(DeviceStatus.AVAILABLE);
        }

        Device saved = deviceRepository.save(device);
        return deviceMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public DeviceResponse getById(String id) {
        Device device = getEntityById(id);
        return deviceMapper.toResponse(device);
    }

    @Override
    @Transactional(readOnly = true)
    public Device getEntityById(String id) {
        return deviceRepository.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.DEVICE_NOT_FOUND,
                        "Device not found with id: " + id));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<DeviceResponse> getAll(Pageable pageable) {
        Page<Device> page = deviceRepository.findAll(pageable);
        Page<DeviceResponse> mappedPage = page.map(deviceMapper::toResponse);
        return PageResponse.from(mappedPage);
    }

    @Override
    @Transactional
    public DeviceResponse update(String id, DeviceUpdateRequest request) {
        Device device = getEntityById(id);

        if (request.getSerialNumber() != null && deviceRepository.existsBySerialNumberAndIdNot(request.getSerialNumber(), id)) {
            throw new ApiException(ErrorCode.DEVICE_SERIAL_NUMBER_EXISTS,
                    "Device serial number already exists: " + request.getSerialNumber());
        }

        if (request.getModelId() != null) {
            DeviceModel deviceModel = deviceModelRepository.findById(request.getModelId())
                    .orElseThrow(() -> new ApiException(ErrorCode.DEVICE_MODEL_NOT_FOUND,
                            "Device model not found with id: " + request.getModelId()));
            device.setDeviceModel(deviceModel);
        }

        deviceMapper.updateEntityFromRequest(request, device);
        Device saved = deviceRepository.save(device);
        return deviceMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public void delete(String id) {
        Device device = getEntityById(id);
        deviceRepository.delete(device);
    }
}
