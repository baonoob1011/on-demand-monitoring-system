package com.ondemandmonitoring.controlgateway.client;

public interface ControlAuthorizationClient {
    ControlAuthorizationContext authorize(String missionId, String droneId, String operatorAccessToken);
}
