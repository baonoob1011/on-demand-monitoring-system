package com.ondemandmonitoring.mission.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ondemandmonitoring.mission.domain.Mission;
import com.ondemandmonitoring.mission.enums.MissionStatus;
import com.ondemandmonitoring.mission.repository.MissionRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MissionServiceTest {

    @Mock
    private MissionRepository missionRepository;

    @InjectMocks
    private MissionService missionService;

    @Test
    void seedObstacleAvoidanceMission_createsCompactMapTarget() {
        when(missionRepository.findByMissionCode("SIM-SEED-OBSTACLE-001")).thenReturn(Optional.empty());
        when(missionRepository.save(any(Mission.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Mission mission = missionService.seedObstacleAvoidanceMission();

        assertThat(mission.getMissionCode()).isEqualTo("SIM-SEED-OBSTACLE-001");
        assertThat(mission.getStatus()).isEqualTo(MissionStatus.READY_TO_FLY);
        assertThat(mission.getAssignedDeviceCode()).isEqualTo("DRONE-01");
        assertThat(mission.getTargetNorthM()).isEqualTo(85.0);
        assertThat(mission.getTargetEastM()).isEqualTo(25.0);
        assertThat(mission.getTargetAltitudeM()).isEqualTo(18.0);
    }

    @Test
    void dispatchNextMission_marksMissionInProgress() {
        Mission mission = new Mission();
        mission.setMissionCode("SIM-SEED-OBSTACLE-001");
        mission.setStatus(MissionStatus.READY_TO_FLY);
        mission.setAssignedDeviceCode("DRONE-01");
        mission.setLatitude(10.0);
        mission.setLongitude(106.0);
        mission.setTargetNorthM(85.0);
        mission.setTargetEastM(25.0);
        mission.setTargetAltitudeM(18.0);

        when(missionRepository.findFirstByAssignedDeviceCodeAndStatusInOrderByCreatedAtAsc(
                any(),
                any())).thenReturn(Optional.of(mission));
        when(missionRepository.save(any(Mission.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Mission dispatched = missionService.dispatchNextMission("DRONE-01");

        assertThat(dispatched.getStatus()).isEqualTo(MissionStatus.IN_PROGRESS);
        assertThat(dispatched.getStartedAt()).isNotNull();

        ArgumentCaptor<Mission> captor = ArgumentCaptor.forClass(Mission.class);
        verify(missionRepository).save(captor.capture());
        assertThat(captor.getValue().getMissionCode()).isEqualTo("SIM-SEED-OBSTACLE-001");
    }
}
