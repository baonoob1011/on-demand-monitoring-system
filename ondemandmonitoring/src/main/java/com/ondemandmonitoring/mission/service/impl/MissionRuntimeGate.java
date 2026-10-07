package com.ondemandmonitoring.mission.service.impl;

import com.ondemandmonitoring.common.exception.ApiException;
import com.ondemandmonitoring.common.exception.ErrorCode;
import com.ondemandmonitoring.mission.domain.Mission;

/** Structural metadata gate; runtime callers must additionally verify Assignment/Execution integrity. */
public final class MissionRuntimeGate {
    private MissionRuntimeGate() {}
    public static boolean allowsLegacyRuntime(Mission mission) {
        return mission.getCreationIdempotencyKey() == null;
    }
    public static void requireLegacyRuntime(Mission mission) {
        if (!allowsLegacyRuntime(mission)) throw new ApiException(ErrorCode.MISSION_PLANNING_ONLY);
    }
    public static boolean isStructurallyFinalized(Mission mission) {
        if (allowsLegacyRuntime(mission)) return true;
        // Mission revision is historical acceptance, not membership in the latest Order revision.
        return mission.getOrder() != null && mission.getFinalizedPlanRevision() != null
                && mission.getFinalizedPlanRevision() > 0
                && mission.getFinalizedPlanRevision() <= mission.getOrder().getExecutionPlanRevision();
    }
    public static void requireFinalizedStructure(Mission mission) {
        if (!isStructurallyFinalized(mission)) throw new ApiException(ErrorCode.CHECKLIST_ASSIGNMENT_NOT_FINALIZED);
    }
}
