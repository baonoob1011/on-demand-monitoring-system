package com.ondemandmonitoring.drone.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ondemandmonitoring.drone.dto.request.TelemetryRequest;
import com.ondemandmonitoring.drone.domain.DroneTelemetry;
import com.ondemandmonitoring.drone.domain.Drone;
import com.ondemandmonitoring.drone.repository.DroneRepository;
import com.ondemandmonitoring.drone.repository.DroneTelemetryRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DroneTelemetryServiceTest {

    @Mock
    private DroneTelemetryRepository repository;

    @Mock
    private DroneRepository droneRepository;

    @InjectMocks
    private DroneTelemetryService service;

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

        Drone drone = new Drone();
        drone.setDroneCode("DRONE-01");
        DroneTelemetry persisted = new DroneTelemetry();
        when(droneRepository.findByDroneCode("DRONE-01")).thenReturn(Optional.of(drone));
        when(repository.findByDroneCode("DRONE-01")).thenReturn(Optional.empty());
        when(repository.save(any(DroneTelemetry.class))).thenReturn(persisted);

        DroneTelemetry result = service.save("DRONE-01", request);

        assertThat(result).isSameAs(persisted);
        ArgumentCaptor<DroneTelemetry> captor = ArgumentCaptor.forClass(DroneTelemetry.class);
        verify(repository).save(captor.capture());
        DroneTelemetry captured = captor.getValue();
        assertThat(captured.getDroneCode()).isEqualTo("DRONE-01");
        assertThat(captured.getDrone()).isSameAs(drone);
        assertThat(captured.getLatitude()).isEqualTo(10.1);
        assertThat(captured.getLongitude()).isEqualTo(106.2);
        assertThat(captured.getAltitude()).isEqualTo(30.0);
        assertThat(captured.getBatteryPercent()).isEqualTo(85.0);
        assertThat(captured.getSpeed()).isEqualTo(12.5);
        assertThat(captured.getFlightMode()).isEqualTo("AUTO");
        assertThat(captured.getArmed()).isTrue();
    }
}
