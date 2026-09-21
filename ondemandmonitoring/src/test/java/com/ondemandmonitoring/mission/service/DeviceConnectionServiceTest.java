package com.ondemandmonitoring.mission.service;

import com.ondemandmonitoring.drone.domain.Drone;
import com.ondemandmonitoring.drone.enums.DroneStatus;
import com.ondemandmonitoring.drone.repository.DroneRepository;
import com.ondemandmonitoring.mission.domain.GcsSession;
import com.ondemandmonitoring.mission.domain.Mission;
import com.ondemandmonitoring.mission.domain.MissionDroneAssignment;
import com.ondemandmonitoring.mission.dto.response.MissionResponse;
import com.ondemandmonitoring.mission.enums.MissionStatus;
import com.ondemandmonitoring.mission.mapper.MissionMapper;
import com.ondemandmonitoring.mission.repository.GcsSessionRepository;
import com.ondemandmonitoring.mission.repository.MissionDroneAssignmentRepository;
import com.ondemandmonitoring.mission.repository.MissionOperatorAssignmentRepository;
import com.ondemandmonitoring.mission.repository.MissionRepository;
import com.ondemandmonitoring.mission.service.impl.DeviceConnectionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeviceConnectionServiceTest {

    @Mock
    private MissionRepository missionRepository;
    @Mock
    private GcsSessionRepository gcsSessionRepository;
    @Mock
    private MissionDroneAssignmentRepository missionDroneAssignmentRepository;
    @Mock
    private MissionOperatorAssignmentRepository missionOperatorAssignmentRepository;
    @Mock
    private DroneRepository droneRepository;
    @Mock
    private MissionMapper missionMapper;

    private DeviceConnectionService deviceConnectionService;

    @BeforeEach
    void setUp() {
        deviceConnectionService = new DeviceConnectionService(
                missionRepository,
                gcsSessionRepository,
                missionDroneAssignmentRepository,
                missionOperatorAssignmentRepository,
                droneRepository,
                missionMapper
        );

        lenient().when(missionMapper.toResponse(any())).thenAnswer(inv -> {
            Mission m = inv.getArgument(0);
            if (m == null) return null;
            return MissionResponse.builder()
                    .id(m.getId())
                    .missionCode(m.getMissionCode())
                    .status(m.getStatus())
                    .build();
        });
    }

    @Test
    @DisplayName("connectGcs creates gcs_sessions / device_connections record with CONNECTED status")
    void connectGcs_Success() {
        String missionId = "M-1";
        Mission mission = new Mission();
        mission.setId(missionId);
        mission.setStatus(MissionStatus.SCHEDULED);

        Drone device = new Drone();
        device.setId("D-1");
        device.setDroneCode("DRONE-01");

        MissionDroneAssignment mda = new MissionDroneAssignment();
        mda.setMission(mission);
        mda.setDrone(device);
        mda.setIsCurrent(true);

        when(missionRepository.findById(missionId)).thenReturn(Optional.of(mission));
        when(missionDroneAssignmentRepository.findByMissionIdAndIsCurrentTrue(missionId)).thenReturn(Optional.of(mda));
        when(missionRepository.save(any(Mission.class))).thenAnswer(inv -> inv.getArgument(0));

        MissionResponse response = deviceConnectionService.connectGcs(missionId);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(MissionStatus.CONNECTED);

        ArgumentCaptor<GcsSession> sessionCaptor = ArgumentCaptor.forClass(GcsSession.class);
        verify(gcsSessionRepository).save(sessionCaptor.capture());
        GcsSession savedSession = sessionCaptor.getValue();
        assertThat(savedSession.getConnectionStatus()).isEqualTo("CONNECTED");
        assertThat(savedSession.getTelemetryActive()).isTrue();
    }

    @Test
    @DisplayName("disconnectGcs sets session status to DISCONNECTED")
    void disconnectGcs_Success() {
        String missionId = "M-1";
        Mission mission = new Mission();
        mission.setId(missionId);
        mission.setStatus(MissionStatus.CONNECTED);

        GcsSession activeSession = new GcsSession();
        activeSession.setMission(mission);
        activeSession.setConnectionStatus("CONNECTED");
        activeSession.setTelemetryActive(true);

        when(missionRepository.findById(missionId)).thenReturn(Optional.of(mission));
        when(gcsSessionRepository.findTopByMissionIdAndConnectionStatusOrderByConnectedAtDesc(missionId, "CONNECTED"))
                .thenReturn(Optional.of(activeSession));

        deviceConnectionService.disconnectGcs(missionId, "MISSION_COMPLETED");

        assertThat(activeSession.getConnectionStatus()).isEqualTo("DISCONNECTED");
        assertThat(activeSession.getTelemetryActive()).isFalse();
        assertThat(activeSession.getDisconnectReason()).isEqualTo("MISSION_COMPLETED");
        verify(gcsSessionRepository).save(activeSession);
    }

    @Test
    @DisplayName("handleGcsSessionLost updates session to LOST and triggers Return-To-Launch (RETURNING)")
    void handleGcsSessionLost_TriggersRTL() {
        String missionId = "M-1";
        Mission mission = new Mission();
        mission.setId(missionId);
        mission.setStatus(MissionStatus.IN_PROGRESS);

        Drone device = new Drone();
        device.setId("D-1");
        device.setStatus(DroneStatus.ACTIVE_MISSION);

        MissionDroneAssignment mda = new MissionDroneAssignment();
        mda.setMission(mission);
        mda.setDrone(device);
        mda.setIsCurrent(true);

        GcsSession activeSession = new GcsSession();
        activeSession.setMission(mission);
        activeSession.setConnectionStatus("CONNECTED");

        when(missionRepository.findById(missionId)).thenReturn(Optional.of(mission));
        when(gcsSessionRepository.findTopByMissionIdAndConnectionStatusOrderByConnectedAtDesc(missionId, "CONNECTED"))
                .thenReturn(Optional.of(activeSession));
        when(missionDroneAssignmentRepository.findByMissionIdAndIsCurrentTrue(missionId)).thenReturn(Optional.of(mda));
        when(missionRepository.save(any(Mission.class))).thenAnswer(inv -> inv.getArgument(0));

        MissionResponse response = deviceConnectionService.handleGcsSessionLost(missionId, "SIGNAL_LOSS_COMM_TIMEOUT");

        assertThat(response.getStatus()).isEqualTo(MissionStatus.RETURNING);
        assertThat(device.getStatus()).isEqualTo(DroneStatus.RETURNING);
        assertThat(activeSession.getConnectionStatus()).isEqualTo("LOST");
        assertThat(activeSession.getDisconnectReason()).isEqualTo("SIGNAL_LOSS_COMM_TIMEOUT");

        verify(gcsSessionRepository).save(activeSession);
        verify(droneRepository).save(device);
    }
}
