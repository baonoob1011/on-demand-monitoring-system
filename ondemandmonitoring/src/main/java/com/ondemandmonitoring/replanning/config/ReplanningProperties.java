package com.ondemandmonitoring.replanning.config;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Getter
@Component
public class ReplanningProperties {

    @Value("${mission.replanning.enabled:true}")
    boolean enabled;

    @Value("${mission.replanning.cooldown-seconds:10}")
    long cooldownSeconds;

    @Value("${mission.replanning.route-deviation-threshold-meters:20.0}")
    double routeDeviationThresholdMeters;

    @Value("${mission.replanning.max-replans-per-mission:5}")
    int maxReplansPerMission;

    @Value("${mission.replanning.minimum-battery-reserve-percent:20.0}")
    double minimumBatteryReservePercent;
}
