package com.ondemandmonitoring.device.service.impl;

import com.ondemandmonitoring.common.exception.ApiException;
import com.ondemandmonitoring.common.exception.ErrorCode;
import com.ondemandmonitoring.device.domain.Device;
import com.ondemandmonitoring.device.enums.DeviceStatus;
import com.ondemandmonitoring.device.repository.DeviceRepository;
import com.ondemandmonitoring.device.service.IDeviceService;
import com.ondemandmonitoring.order.repository.OrderRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class DeviceService implements IDeviceService {

    DeviceRepository deviceRepository;
    OrderRepository orderRepository;

    @Override
    public List<Device> getAvailableDrones(String orderId) {
        // 1. Validate if orderId is correct
        boolean orderExists = orderRepository.existsById(orderId);
        if (!orderExists) {
            throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Order not found with ID: " + orderId);
        }

        // 2. Fetch available drones
        List<Device> availableDrones = deviceRepository.findByStatus(DeviceStatus.AVAILABLE);
        
        // Note: If availableDrones is empty, it will return an empty list [] with status 200 OK.
        // The frontend/caller should handle the empty array.
        
        return availableDrones;
    }
}
