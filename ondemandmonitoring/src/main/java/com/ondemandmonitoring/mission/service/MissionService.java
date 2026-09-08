package com.ondemandmonitoring.mission.service;

import com.ondemandmonitoring.mission.domain.Mission;
import com.ondemandmonitoring.mission.enums.MissionStatus;
import com.ondemandmonitoring.mission.repository.MissionRepository;
import java.time.Instant;
import java.util.List;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class MissionService {

    MissionRepository missionRepository;

    @Transactional
    public Mission seedObstacleAvoidanceMission() {
        return missionRepository.findByMissionCode("SIM-SEED-OBSTACLE-001")
                .orElseGet(() -> {
                    Mission mission = new Mission();
                    mission.setMissionCode("SIM-SEED-OBSTACLE-001");
                    mission.setStatus(MissionStatus.READY_TO_FLY);
                    mission.setLatitude(10.0);
                    mission.setLongitude(106.0);
                    mission.setAddress("Gazebo compact map obstacle-avoidance test target");
                    mission.setAssignedDeviceCode("DRONE-01");
                    mission.setTargetNorthM(85.0);
                    mission.setTargetEastM(25.0);
                    mission.setTargetAltitudeM(18.0);
                    mission.setScheduledStartAt(Instant.now());
                    mission.setDescription("Seed mission for 3D planner: fly from compact-map pad toward a target beyond construction obstacles.");
                    return missionRepository.save(mission);
                });
    }

    @Transactional
    public Mission dispatchNextMission(String deviceCode) {
        List<MissionStatus> dispatchable = List.of(
                MissionStatus.READY_TO_FLY,
                MissionStatus.SCHEDULED,
                MissionStatus.CREATED);

        Mission mission = missionRepository
                .findFirstByAssignedDeviceCodeAndStatusInOrderByCreatedAtAsc(deviceCode, dispatchable)
                .orElseGet(this::seedObstacleAvoidanceMission);

        if (mission.getStartedAt() == null) {
            mission.setStartedAt(Instant.now());
        }
        mission.setStatus(MissionStatus.IN_PROGRESS);

        return missionRepository.save(mission);
    }
}
