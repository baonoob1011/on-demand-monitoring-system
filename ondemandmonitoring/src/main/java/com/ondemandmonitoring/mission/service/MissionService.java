package com.ondemandmonitoring.mission.service;

import com.ondemandmonitoring.common.exception.ApiException;
import com.ondemandmonitoring.mission.domain.Mission;
import com.ondemandmonitoring.mission.enums.MissionStatus;
import com.ondemandmonitoring.mission.repository.MissionRepository;
import java.time.Instant;
import java.util.List;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class MissionService {

    MissionRepository missionRepository;

    @Transactional
    public Mission dispatchNextMission(String deviceCode) {
        List<MissionStatus> dispatchable = List.of(
                MissionStatus.READY_TO_FLY,
                MissionStatus.SCHEDULED,
                MissionStatus.CREATED);

        Mission mission = missionRepository
                .findFirstByAssignedDeviceCodeAndStatusInOrderByCreatedAtAsc(deviceCode, dispatchable)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "No dispatchable mission for device " + deviceCode));

        if (mission.getStartedAt() == null) {
            mission.setStartedAt(Instant.now());
        }
        mission.setStatus(MissionStatus.IN_PROGRESS);

        return missionRepository.save(mission);
    }
}
