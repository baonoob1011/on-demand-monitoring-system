package com.ondemandmonitoring.device.service;

import com.ondemandmonitoring.device.domain.Device;
import java.util.List;

public interface IDeviceService {
    List<Device> getAvailableDrones(String orderId);
}
