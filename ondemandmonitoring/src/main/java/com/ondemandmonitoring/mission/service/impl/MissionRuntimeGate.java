package com.ondemandmonitoring.mission.service.impl;

import com.ondemandmonitoring.common.exception.ApiException;
import com.ondemandmonitoring.common.exception.ErrorCode;
import com.ondemandmonitoring.mission.domain.Mission;

/** Phase 4: only pre-cutover legacy Missions may use the whole-Order runtime. */
public final class MissionRuntimeGate {
    private MissionRuntimeGate() {}
    public static boolean allowsLegacyRuntime(Mission mission) {
        return mission.getCreationIdempotencyKey() == null;
    }
    public static void requireLegacyRuntime(Mission mission) {
        if (!allowsLegacyRuntime(mission)) throw new ApiException(ErrorCode.MISSION_PLANNING_ONLY);
    }
}
