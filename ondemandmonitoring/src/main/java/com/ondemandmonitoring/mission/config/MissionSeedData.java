package com.ondemandmonitoring.mission.config;

import com.ondemandmonitoring.mission.service.MissionService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class MissionSeedData implements CommandLineRunner {

    MissionService missionService;

    @Override
    public void run(String... args) {
        missionService.seedObstacleAvoidanceMission();
    }
}
