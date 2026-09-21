package com.ondemandmonitoring.device.service;

import com.ondemandmonitoring.common.api.PageResponse;
import com.ondemandmonitoring.device.domain.DeviceType;
import com.ondemandmonitoring.device.dto.request.DeviceTypeCreateRequest;
import com.ondemandmonitoring.device.dto.request.DeviceTypeUpdateRequest;
import com.ondemandmonitoring.device.dto.response.DeviceTypeResponse;
import org.springframework.data.domain.Pageable;

public interface IDeviceTypeService {

    DeviceTypeResponse create(DeviceTypeCreateRequest request);

    DeviceTypeResponse getById(String id);

    DeviceType getEntityById(String id);

    PageResponse<DeviceTypeResponse> getAll(Pageable pageable);

    DeviceTypeResponse update(String id, DeviceTypeUpdateRequest request);

    void delete(String id);
}
