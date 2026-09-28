package com.ondemandmonitoring.environment.dto.request;

public record WeatherPreflightCheckRequest(
        String missionId,
        String deviceId,
        Double latitude,
        Double longitude) {
}
