package com.ondemandmonitoring.environment.dto.response;

import java.time.Instant;
import java.util.List;

public record WeatherPreflightCheckResponse(
        String status,
        boolean safeToFly,
        String summary,
        double windSpeedMps,
        double windGustMps,
        double precipitationMmH,
        double visibilityKm,
        double temperatureC,
        int humidityPercent,
        List<String> advisories,
        Instant checkedAt) {
}
