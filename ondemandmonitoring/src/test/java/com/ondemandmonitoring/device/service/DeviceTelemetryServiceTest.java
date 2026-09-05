package com.ondemandmonitoring.device.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ondemandmonitoring.device.dto.request.TelemetryRequest;
import com.ondemandmonitoring.device.domain.DeviceTelemetry;
import com.ondemandmonitoring.device.repository.DeviceTelemetryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DeviceTelemetryServiceTest {

    @Mock
    private DeviceTelemetryRepository repository;

    @InjectMocks
    private DeviceTelemetryService service;

    @Test
    void save_mapsRequestAndPersistsTelemetry() {
        TelemetryRequest request = new TelemetryRequest();
        request.setLatitude(10.1);
        request.setLongitude(106.2);
        request.setAltitude(30.0);
        request.setBatteryPercent(85.0);
        request.setSpeed(12.5);
        request.setFlightMode("AUTO");
        request.setArmed(true);

        DeviceTelemetry persisted = new DeviceTelemetry();
        when(repository.save(any(DeviceTelemetry.class))).thenReturn(persisted);

        DeviceTelemetry result = service.save("DRONE-01", request);

        assertThat(result).isSameAs(persisted);
        ArgumentCaptor<DeviceTelemetry> captor = ArgumentCaptor.forClass(DeviceTelemetry.class);
        verify(repository).save(captor.capture());
        DeviceTelemetry captured = captor.getValue();
        assertThat(captured.getDeviceCode()).isEqualTo("DRONE-01");
        assertThat(captured.getLatitude()).isEqualTo(10.1);
        assertThat(captured.getLongitude()).isEqualTo(106.2);
        assertThat(captured.getAltitude()).isEqualTo(30.0);
        assertThat(captured.getBatteryPercent()).isEqualTo(85.0);
        assertThat(captured.getSpeed()).isEqualTo(12.5);
        assertThat(captured.getFlightMode()).isEqualTo("AUTO");
        assertThat(captured.getArmed()).isTrue();
    }
}
