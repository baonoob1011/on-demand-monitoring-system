package com.ondemandmonitoring.replanning.service;

import com.ondemandmonitoring.common.exception.ApiException;
import com.ondemandmonitoring.common.exception.ErrorCode;
import com.ondemandmonitoring.drone.domain.DroneTelemetry;
import com.ondemandmonitoring.mission.domain.MissionPlan;
import com.ondemandmonitoring.planning.service.MissionPlanningService;
import com.ondemandmonitoring.replanning.domain.ReplanningReason;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MissionReplanningServiceImpl implements MissionReplanningService {

    private final MissionPlanningService missionPlanningService;

    @Override
    public MissionPlan replanFromTelemetry(String missionId, DroneTelemetry telemetry, ReplanningReason reason) {
        if (telemetry.getSimX() == null || telemetry.getSimY() == null
                || !Double.isFinite(telemetry.getSimX()) || !Double.isFinite(telemetry.getSimY())) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "Cannot replan without valid telemetry simX/simY.");
        }
        return missionPlanningService.replanAStarEnergyAwareFromCurrentPosition(
                missionId,
                telemetry.getSimX(),
                telemetry.getSimY(),
                reason);
    }
}
