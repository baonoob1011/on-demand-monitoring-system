package com.ondemandmonitoring.device.service;

import com.ondemandmonitoring.common.api.PageResponse;
import com.ondemandmonitoring.device.domain.Device;
import com.ondemandmonitoring.device.dto.request.DeviceCreateRequest;
import com.ondemandmonitoring.device.dto.request.DeviceUpdateRequest;
import com.ondemandmonitoring.device.dto.response.DeviceResponse;
import org.springframework.data.domain.Pageable;

public interface IDeviceService {

    DeviceResponse create(DeviceCreateRequest request);

    DeviceResponse getById(String id);

    Device getEntityById(String id);

    PageResponse<DeviceResponse> getAll(Pageable pageable);

    DeviceResponse update(String id, DeviceUpdateRequest request);

    void delete(String id);
}
