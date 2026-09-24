package com.ondemandmonitoring.drone.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ondemandmonitoring.drone.domain.Drone;
import com.ondemandmonitoring.drone.domain.DroneModel;
import com.ondemandmonitoring.drone.domain.DronePayload;
import com.ondemandmonitoring.drone.enums.DroneStatus;
import com.ondemandmonitoring.drone.repository.DroneModelRepository;
import com.ondemandmonitoring.drone.repository.DronePayloadRepository;
import com.ondemandmonitoring.drone.repository.DroneRepository;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DroneSeedDataInitializerTest {

    @Mock
    private DroneModelRepository modelRepository;

    @Mock
    private DronePayloadRepository payloadRepository;

    @Mock
    private DroneRepository droneRepository;

    @InjectMocks
    private DroneSeedDataInitializer initializer;

    @Test
    void createsFiveMissingDronesWithDistinctCameraPayloads() {
        when(droneRepository.findByDroneCode(anyString())).thenReturn(Optional.empty());
        when(modelRepository.findByModelCode("X500-SITL")).thenReturn(Optional.empty());
        when(payloadRepository.findByModelNameIgnoreCase(anyString())).thenReturn(Optional.empty());
        when(modelRepository.save(any(DroneModel.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(payloadRepository.save(any(DronePayload.class))).thenAnswer(invocation -> invocation.getArgument(0));

        initializer.run(null);

        ArgumentCaptor<Drone> droneCaptor = ArgumentCaptor.forClass(Drone.class);
        verify(droneRepository, times(5)).save(droneCaptor.capture());
        assertThat(droneCaptor.getAllValues())
                .extracting(Drone::getDroneCode)
                .containsExactly("DRN-0048", "DRN-0049", "DRN-0050", "DRN-0051", "DRN-0052");
        assertThat(droneCaptor.getAllValues())
                .allSatisfy(drone -> {
                    assertThat(drone.getSerialNumber()).isEqualTo("SIM-X500-" + drone.getDroneCode());
                    assertThat(drone.getStatus()).isEqualTo(DroneStatus.AVAILABLE);
                    assertThat(drone.getDroneModel().getModelCode()).isEqualTo("X500-SITL");
                    assertThat(drone.getDronePayload()).isNotNull();
                });
        Set<String> cameraNames = droneCaptor.getAllValues().stream()
                .map(drone -> drone.getDronePayload().getModelName())
                .collect(Collectors.toSet());
        assertThat(cameraNames).hasSize(5);
        verify(payloadRepository, times(5)).save(any(DronePayload.class));
    }

    @Test
    void leavesExistingDroneUntouched() {
        Drone existing = new Drone();
        existing.setDroneCode("DRN-0048");
        existing.setStatus(DroneStatus.RESERVED);
        when(droneRepository.findByDroneCode(anyString())).thenReturn(Optional.of(existing));

        initializer.run(null);

        verify(droneRepository, never()).save(any());
        verifyNoInteractions(modelRepository, payloadRepository);
        assertThat(existing.getStatus()).isEqualTo(DroneStatus.RESERVED);
    }

    @Test
    void skipsSerialConflictWithoutWriting() {
        when(droneRepository.findByDroneCode(anyString())).thenReturn(Optional.empty());
        when(droneRepository.existsBySerialNumber(anyString())).thenReturn(true);

        initializer.run(null);

        verify(droneRepository, never()).save(any());
        verifyNoInteractions(modelRepository, payloadRepository);
    }

    @Test
    void createsOnlyMissingDronesWithoutChangingExistingOne() {
        Drone existing = new Drone();
        existing.setDroneCode("DRN-0048");
        existing.setStatus(DroneStatus.RESERVED);
        when(droneRepository.findByDroneCode(anyString())).thenAnswer(invocation ->
                "DRN-0048".equals(invocation.getArgument(0))
                        ? Optional.of(existing)
                        : Optional.empty());
        when(modelRepository.findByModelCode("X500-SITL")).thenReturn(Optional.of(new DroneModel()));
        when(payloadRepository.findByModelNameIgnoreCase(anyString())).thenReturn(Optional.empty());
        when(payloadRepository.save(any(DronePayload.class))).thenAnswer(invocation -> invocation.getArgument(0));

        initializer.run(null);

        verify(droneRepository, times(4)).save(any(Drone.class));
        verify(payloadRepository, times(4)).save(any(DronePayload.class));
        assertThat(existing.getStatus()).isEqualTo(DroneStatus.RESERVED);
    }
}
