package com.ondemandmonitoring.device.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ondemandmonitoring.device.domain.Device;
import com.ondemandmonitoring.device.domain.DeviceModel;
import com.ondemandmonitoring.device.domain.DeviceType;
import com.ondemandmonitoring.device.enums.DeviceStatus;
import com.ondemandmonitoring.device.repository.DeviceModelRepository;
import com.ondemandmonitoring.device.repository.DeviceRepository;
import com.ondemandmonitoring.device.repository.DeviceTypeRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DeviceSeedDataInitializerTest {

    @Mock
    private DeviceTypeRepository deviceTypeRepository;

    @Mock
    private DeviceModelRepository deviceModelRepository;

    @Mock
    private DeviceRepository deviceRepository;

    @InjectMocks
    private DeviceSeedDataInitializer initializer;

    @Test
    void seedsDevicesWhenMissing() {
        when(deviceTypeRepository.findByCode(anyString())).thenReturn(Optional.empty());
        when(deviceTypeRepository.save(any(DeviceType.class))).thenAnswer(invocation -> invocation.getArgument(0));

        when(deviceModelRepository.findByCode(anyString())).thenReturn(Optional.empty());
        when(deviceModelRepository.save(any(DeviceModel.class))).thenAnswer(invocation -> invocation.getArgument(0));

        when(deviceRepository.findBySerialNumber(anyString())).thenReturn(Optional.empty());
        when(deviceRepository.save(any(Device.class))).thenAnswer(invocation -> invocation.getArgument(0));

        initializer.run(null);

        verify(deviceTypeRepository, times(2)).save(any(DeviceType.class));
        verify(deviceModelRepository, times(1)).save(any(DeviceModel.class));

        ArgumentCaptor<Device> deviceCaptor = ArgumentCaptor.forClass(Device.class);
        verify(deviceRepository, times(3)).save(deviceCaptor.capture());

        assertThat(deviceCaptor.getAllValues())
                .extracting(Device::getSerialNumber)
                .containsExactly("ODM-DRN-0052", "ODM-DRN-0053", "ODM-DRN-0054");

        assertThat(deviceCaptor.getAllValues())
                .allSatisfy(device -> {
                    assertThat(device.getStatus()).isEqualTo(DeviceStatus.AVAILABLE);
                    assertThat(device.getDeviceModel()).isNotNull();
                });
    }

    @Test
    void leavesExistingDevicesUntouched() {
        DeviceType mockType = new DeviceType();
        when(deviceTypeRepository.findByCode(anyString())).thenReturn(Optional.of(mockType));

        DeviceModel mockModel = new DeviceModel();
        when(deviceModelRepository.findByCode(anyString())).thenReturn(Optional.of(mockModel));

        Device existingDevice = new Device();
        existingDevice.setSerialNumber("ODM-DRN-0052");
        existingDevice.setStatus(DeviceStatus.RESERVED);

        when(deviceRepository.findBySerialNumber(anyString())).thenReturn(Optional.of(existingDevice));

        initializer.run(null);

        verify(deviceRepository, never()).save(any(Device.class));
        verify(deviceTypeRepository, never()).save(any(DeviceType.class));
        verify(deviceModelRepository, never()).save(any(DeviceModel.class));
    }
}
