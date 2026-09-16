package com.ondemandmonitoring.controlgateway.client;

public record ControlAuthorizationContext(
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
