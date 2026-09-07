package com.ondemandmonitoring.mission.enums;

public enum MissionStatus {

    CREATED,

    RESOURCE_ASSIGNING,

    WAITING_OPERATOR_ACCEPTANCE,

    /** Operator on-site, mission assigned and acknowledged. */
    SCHEDULED,

    /** Drone powered on, telemetry link with GCS app confirmed. */
    CONNECTED,

    /** Operator opened checklist; digital preflight validation in progress. */
    PREFLIGHT_CHECKING,

    /**
     * All preflight checks passed; flight-access token issued.
     * Operator confirms take-off on the web dashboard.
     */
    READY_TO_FLY,

    /**
     * Preflight failed; operator notified.
     * Drone will be routed to maintenance or charge station and order re-queued.
     */
    FAILED_PREFLIGHT,

    /**
     * Order re-queued to Manager after preflight failure.
     * Mission will be cancelled and Manager re-assigns drone + operator.
     */
    PENDING_APPROVAL,

    /** Drone airborne, transmitting realtime GPS + video stream. */
    IN_FLIGHT,

    /** Legacy alias kept for compatibility – maps to IN_FLIGHT in most contexts. */
    IN_PROGRESS,

    RETURNING,

    POSTFLIGHT_CHECKING,

    COMPLETED,

    FAILED,

    CANCELLED
}
