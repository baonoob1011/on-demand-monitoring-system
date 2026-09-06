package com.ondemandmonitoring.mission.enums;


public enum MissionStatus {

    CREATED,

    RESOURCE_ASSIGNING,

    WAITING_OPERATOR_ACCEPTANCE,

    SCHEDULED,

    PREFLIGHT_CHECKING,

    READY_TO_FLY,

    IN_PROGRESS,

    RETURNING,

    POSTFLIGHT_CHECKING,

    COMPLETED,

    FAILED,

    CANCELLED
}