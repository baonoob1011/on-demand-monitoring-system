package com.ondemandmonitoring.device.service;

import com.ondemandmonitoring.common.api.PageResponse;
import com.ondemandmonitoring.device.domain.DeviceModel;
import com.ondemandmonitoring.device.dto.request.DeviceModelCreateRequest;
import com.ondemandmonitoring.device.dto.request.DeviceModelUpdateRequest;
import com.ondemandmonitoring.device.dto.response.DeviceModelResponse;
import org.springframework.data.domain.Pageable;

public interface IDeviceModelService {

    DeviceModelResponse create(DeviceModelCreateRequest request);

    DeviceModelResponse getById(String id);

    DeviceModel getEntityById(String id);

    PageResponse<DeviceModelResponse> getAll(Pageable pageable);

    DeviceModelResponse update(String id, DeviceModelUpdateRequest request);

    void delete(String id);
}
