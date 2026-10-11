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

    public static boolean hasActiveSafeStop(Mission mission) {
        return mission.getSafeStopStatus() != null || mission.getSafeStopRequestedAt() != null
                || mission.getSafeStopConfirmedAt() != null || mission.getSafeStopCleanedAt() != null;
    }

    public static void requireOperationalCommandsAllowed(Mission mission) {
        if (mission.getStatus() == com.ondemandmonitoring.mission.enums.MissionStatus.CANCELLED
                || hasActiveSafeStop(mission))
            throw new ApiException(ErrorCode.MISSION_CANCELLATION_REQUIRES_SAFE_STOP);
    }

    /** Returning and postflight terminate operation; they never reopen a confirmed stop. */
    public static void requireTerminationTransitionAllowed(Mission mission) {
        if (mission.getSafeStopConfirmedAt() != null || mission.getSafeStopCleanedAt() != null
                || mission.getSafeStopStatus() == com.ondemandmonitoring.mission.enums.MissionSafeStopStatus.READY_FOR_CANCELLATION)
            throw new ApiException(ErrorCode.MISSION_SAFE_STOP_NOT_READY);
    }

    public static void requireSessionAllowed(Mission mission) {
        requireTerminationTransitionAllowed(mission);
        if (mission.getStatus() == com.ondemandmonitoring.mission.enums.MissionStatus.CANCELLED
                || mission.getStatus() == com.ondemandmonitoring.mission.enums.MissionStatus.FAILED
                || mission.getStatus() == com.ondemandmonitoring.mission.enums.MissionStatus.COMPLETED
                || mission.getSafeStopStatus() == com.ondemandmonitoring.mission.enums.MissionSafeStopStatus.IN_PROGRESS
                || mission.getSafeStopStatus() == com.ondemandmonitoring.mission.enums.MissionSafeStopStatus.READY_FOR_CANCELLATION)
            throw new ApiException(ErrorCode.MISSION_SAFE_STOP_NOT_READY);
    }

    public static boolean allowsConfirmedGroundPostflight(Mission mission) {
        return mission.getSafeStopRequestedAt() != null && mission.getSafeStopConfirmedAt() != null
                && mission.getSafeStopCleanedAt() == null
                && mission.getSafeStopStatus() == com.ondemandmonitoring.mission.enums.MissionSafeStopStatus.IN_PROGRESS
                && mission.getActualStartAt() == null && !Boolean.TRUE.equals(mission.getSafeStopRequiresLanding())
                && java.util.Set.of(com.ondemandmonitoring.mission.enums.MissionStatus.CONNECTED,
                    com.ondemandmonitoring.mission.enums.MissionStatus.PREFLIGHT_CHECKING,
                    com.ondemandmonitoring.mission.enums.MissionStatus.READY_TO_FLY).contains(mission.getStatus());
    }

    public static void requirePostflightTransitionAllowed(Mission mission) {
        if (!allowsConfirmedGroundPostflight(mission)) requireTerminationTransitionAllowed(mission);
    }
}
