package com.ondemandmonitoring.environment.controller;

import com.ondemandmonitoring.common.api.ApiResponse;
import com.ondemandmonitoring.common.exception.ApiException;
import com.ondemandmonitoring.common.exception.ErrorCode;
import com.ondemandmonitoring.environment.domain.MissionWeatherCheck;
import com.ondemandmonitoring.environment.dto.request.WeatherPreflightCheckRequest;
import com.ondemandmonitoring.environment.dto.response.WeatherPreflightCheckResponse;
import com.ondemandmonitoring.environment.repository.MissionWeatherCheckRepository;
import com.ondemandmonitoring.mission.domain.Mission;
import com.ondemandmonitoring.mission.repository.MissionRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Weather Preflight", description = "Weather safety checks before drone takeoff")
@RestController
@RequestMapping("/api/weather")
@RequiredArgsConstructor
public class WeatherPreflightController {

    private final MissionRepository missionRepository;
    private final MissionWeatherCheckRepository weatherCheckRepository;

    @Operation(summary = "Check weather before takeoff", description = "Returns a deterministic weather safety verdict for the current mission preflight flow")
    @PostMapping("/preflight-check")
    public ResponseEntity<ApiResponse<WeatherPreflightCheckResponse>> check(
            @RequestBody(required = false) WeatherPreflightCheckRequest request) {
        double latitude = request != null && request.latitude() != null ? request.latitude() : 10.6402;
        double longitude = request != null && request.longitude() != null ? request.longitude() : 106.6912;

        double locationSeed = Math.abs(latitude * 31.0 + longitude * 17.0);
        double windSpeedMps = round1(4.2 + locationSeed % 3.4);
        double windGustMps = round1(windSpeedMps + 2.1);
        double precipitationMmH = round1((locationSeed % 2.0) * 0.4);
        double visibilityKm = round1(9.0 + locationSeed % 4.0);
        double temperatureC = round1(28.0 + locationSeed % 3.0);
        int humidityPercent = (int) Math.round(68 + locationSeed % 12);

        List<String> advisories = new ArrayList<>();
        if (windSpeedMps > 8.0 || windGustMps > 11.0) {
            advisories.add("Gió mạnh, cần hoãn cất cánh hoặc giảm trần bay.");
        }
        if (precipitationMmH > 1.0) {
            advisories.add("Có mưa, cần kiểm tra chống nước payload/camera.");
        }
        if (visibilityKm < 5.0) {
            advisories.add("Tầm nhìn thấp, không đủ điều kiện bay an toàn.");
        }
        if (advisories.isEmpty()) {
            advisories.add("Thời tiết ổn định, đủ điều kiện bay mô phỏng.");
        }

        boolean safeToFly = windSpeedMps <= 8.0
                && windGustMps <= 11.0
                && precipitationMmH <= 1.0
                && visibilityKm >= 5.0;
        String status = safeToFly ? "PASS" : "WARN";
        String summary = safeToFly
                ? "Thời tiết phù hợp để cất cánh."
                : "Thời tiết cần chú ý trước khi cất cánh.";

        Instant checkedAt = Instant.now();
        WeatherPreflightCheckResponse response = new WeatherPreflightCheckResponse(
                null,
                request != null ? request.missionId() : null,
                request != null ? request.droneCode() : null,
                status,
                safeToFly,
                summary,
                windSpeedMps,
                windGustMps,
                precipitationMmH,
                visibilityKm,
                temperatureC,
                humidityPercent,
                advisories,
                checkedAt);

        if (request != null && request.missionId() != null && !request.missionId().isBlank()) {
            Mission mission = missionRepository.findById(request.missionId())
                    .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Mission not found: " + request.missionId()));
            MissionWeatherCheck saved = new MissionWeatherCheck();
            saved.setMission(mission);
            saved.setDroneCode(request.droneCode());
            saved.setStatus(status);
            saved.setSafeToFly(safeToFly);
            saved.setSummary(summary);
            saved.setWindSpeedMps(windSpeedMps);
            saved.setWindGustMps(windGustMps);
            saved.setPrecipitationMmH(precipitationMmH);
            saved.setVisibilityKm(visibilityKm);
            saved.setTemperatureC(temperatureC);
            saved.setHumidityPercent(humidityPercent);
            saved.setAdvisories(String.join("\n", advisories));
            saved.setCheckedAt(checkedAt);
            response = toResponse(weatherCheckRepository.save(saved));
        }

        return ResponseEntity.ok(ApiResponse.ok("Weather preflight checked", response));
    }

    @Operation(summary = "Get latest mission weather preflight check")
    @GetMapping("/preflight-checks/latest")
    public ResponseEntity<ApiResponse<WeatherPreflightCheckResponse>> latest(
            @RequestParam String missionId) {
        MissionWeatherCheck check = weatherCheckRepository.findFirstByMissionIdOrderByCreatedAtDesc(missionId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Weather preflight check not found"));
        return ResponseEntity.ok(ApiResponse.ok("Latest weather preflight check", toResponse(check)));
    }

    private static WeatherPreflightCheckResponse toResponse(MissionWeatherCheck check) {
        List<String> advisories = check.getAdvisories() == null || check.getAdvisories().isBlank()
                ? List.of()
                : Arrays.asList(check.getAdvisories().split("\\R"));
        return new WeatherPreflightCheckResponse(
                check.getId(),
                check.getMission().getId(),
                check.getDroneCode(),
                check.getStatus(),
                Boolean.TRUE.equals(check.getSafeToFly()),
                check.getSummary(),
                check.getWindSpeedMps(),
                check.getWindGustMps(),
                check.getPrecipitationMmH(),
                check.getVisibilityKm(),
                check.getTemperatureC(),
                check.getHumidityPercent(),
                advisories,
                check.getCheckedAt());
    }

    private static double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
