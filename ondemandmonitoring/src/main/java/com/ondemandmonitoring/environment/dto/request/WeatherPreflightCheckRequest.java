package com.ondemandmonitoring.environment.dto.request;

public record WeatherPreflightCheckRequest(
        String missionId,
        String droneCode,
        Double latitude,
        Double longitude) {
}
