package com.ondemandmonitoring.mission.dto.response;

public record MissionControlContextResponse(
        String missionId,
        String missionCode,
        String droneId,
        String operatorId,
        String missionStatus,
        boolean controlAllowed,
        boolean mediaCaptureAllowed,
        boolean mediaUploadAllowed
) {
}
