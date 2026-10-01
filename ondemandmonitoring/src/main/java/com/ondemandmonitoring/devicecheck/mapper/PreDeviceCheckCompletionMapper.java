package com.ondemandmonitoring.devicecheck.mapper;

import com.ondemandmonitoring.device.domain.Device;
import com.ondemandmonitoring.devicecheck.domain.PersistedPreDeviceCheck;
import com.ondemandmonitoring.devicecheck.domain.PersistedPreDeviceCheckItem;
import com.ondemandmonitoring.devicecheck.dto.response.PreDeviceCheckResponse;
import com.ondemandmonitoring.devicecheck.enums.PreDeviceCheckType;
import com.ondemandmonitoring.devicecheck.enums.PreDeviceItemStatus;
import com.ondemandmonitoring.environment.domain.MissionWeatherCheck;
import com.ondemandmonitoring.mission.domain.FlightToken;
import com.ondemandmonitoring.mission.domain.Mission;
import com.ondemandmonitoring.mission.mapper.FlightTokenMapper;
import java.time.Instant;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(
        componentModel = "spring",
        uses = FlightTokenMapper.class,
        imports = PreDeviceCheckType.class)
public interface PreDeviceCheckCompletionMapper {

    @Mapping(target = "id", source = "persistedPreDeviceCheck.id")
    @Mapping(target = "deviceCode", source = "assignedDevice.id")
    @Mapping(target = "missionId", source = "mission.id")
    @Mapping(target = "overallPassed", expression = "java(preDeviceCheckPassed(persistedPreDeviceCheck))")
    @Mapping(target = "failureReason", expression = "java(failureReason(persistedPreDeviceCheck))")
    @Mapping(target = "faultType", expression = "java(faultType(persistedPreDeviceCheck))")
    @Mapping(target = "batteryPercent", expression = "java(parseLeadingDouble(messageFor(persistedPreDeviceCheck, PreDeviceCheckType.BATTERY)))")
    @Mapping(target = "gpsFixType", expression = "java(gpsFixType(persistedPreDeviceCheck))")
    @Mapping(target = "gpsSatelliteCount", ignore = true)
    @Mapping(target = "gyrometerOk", expression = "java(passed(persistedPreDeviceCheck, PreDeviceCheckType.MAVSDK_HEALTH))")
    @Mapping(target = "accelerometerOk", expression = "java(passed(persistedPreDeviceCheck, PreDeviceCheckType.MAVSDK_HEALTH))")
    @Mapping(target = "magnetometerOk", expression = "java(passed(persistedPreDeviceCheck, PreDeviceCheckType.MAVSDK_HEALTH))")
    @Mapping(target = "localPositionOk", expression = "java(passed(persistedPreDeviceCheck, PreDeviceCheckType.LOCAL_POSITION))")
    @Mapping(target = "globalPositionOk", expression = "java(passed(persistedPreDeviceCheck, PreDeviceCheckType.LOCAL_POSITION))")
    @Mapping(target = "homePositionOk", expression = "java(passed(persistedPreDeviceCheck, PreDeviceCheckType.LOCAL_POSITION))")
    @Mapping(target = "armable", expression = "java(passed(persistedPreDeviceCheck, PreDeviceCheckType.MAVSDK_HEALTH))")
    @Mapping(target = "connected", expression = "java(passed(persistedPreDeviceCheck, PreDeviceCheckType.MAVSDK))")
    @Mapping(target = "inAir", constant = "false")
    @Mapping(target = "flightMode", expression = "java(flightMode(persistedPreDeviceCheck))")
    @Mapping(target = "cameraOk", expression = "java(passed(persistedPreDeviceCheck, PreDeviceCheckType.CAMERA))")
    @Mapping(target = "gimbalOk", expression = "java(passed(persistedPreDeviceCheck, PreDeviceCheckType.CAMERA))")
    @Mapping(target = "storageAvailableMb", ignore = true)
    @Mapping(target = "storageOk", expression = "java(passed(persistedPreDeviceCheck, PreDeviceCheckType.MEDIA))")
    @Mapping(target = "weatherOk", expression = "java(weatherOk(weatherCheck))")
    @Mapping(target = "weatherNotes", expression = "java(weatherNotes(weatherCheck))")
    @Mapping(target = "flightToken", source = "token")
    @Mapping(target = "checkedAt", expression = "java(checkedAt(persistedPreDeviceCheck))")
    PreDeviceCheckResponse toResponse(
            Mission mission,
            Device assignedDevice,
            PersistedPreDeviceCheck persistedPreDeviceCheck,
            FlightToken token,
            MissionWeatherCheck weatherCheck);

    default boolean passed(PersistedPreDeviceCheck run, PreDeviceCheckType checkType) {
        return run.getItems().stream()
                .filter(item -> checkType.code().equals(item.getCheckType()))
                .findFirst()
                .map(item -> item.getStatus() == PreDeviceItemStatus.PASSED)
                .orElse(false);
    }

    default String messageFor(PersistedPreDeviceCheck run, PreDeviceCheckType checkType) {
        return run.getItems().stream()
                .filter(item -> checkType.code().equals(item.getCheckType()))
                .findFirst()
                .map(PersistedPreDeviceCheckItem::getMessage)
                .orElse(null);
    }

    default Double parseLeadingDouble(String value) {
        if (value == null || value.isBlank()) return null;
        String token = value.strip().split("\\s+")[0].replace("%", "");
        try {
            return Double.parseDouble(token);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    default Instant checkedAt(PersistedPreDeviceCheck run) {
        return run.getCompletedAt() == null ? Instant.now() : run.getCompletedAt();
    }

    default boolean preDeviceCheckPassed(PersistedPreDeviceCheck run) {
        return run.getItems().stream()
                .allMatch(item -> item.getStatus() == PreDeviceItemStatus.PASSED);
    }

    default String failureReason(PersistedPreDeviceCheck run) {
        return run.getItems().stream()
                .filter(item -> item.getStatus() == PreDeviceItemStatus.FAILED)
                .map(PersistedPreDeviceCheckItem::getMessage)
                .filter(message -> message != null && !message.isBlank())
                .findFirst()
                .orElse(null);
    }

    default String faultType(PersistedPreDeviceCheck run) {
        return run.getItems().stream()
                .filter(item -> item.getStatus() == PreDeviceItemStatus.FAILED)
                .map(PersistedPreDeviceCheckItem::getCheckType)
                .findFirst()
                .orElse(null);
    }

    default String gpsFixType(PersistedPreDeviceCheck run) {
        return passed(run, PreDeviceCheckType.LOCAL_POSITION)
                ? PreDeviceCheckType.LOCAL_POSITION.code()
                : null;
    }

    default String flightMode(PersistedPreDeviceCheck run) {
        return passed(run, PreDeviceCheckType.PX4_CONTROL)
                ? PreDeviceCheckType.PX4_CONTROL.code()
                : null;
    }

    default Boolean weatherOk(MissionWeatherCheck weatherCheck) {
        return weatherCheck == null ? null : weatherCheck.getSafeToFly();
    }

    default String weatherNotes(MissionWeatherCheck weatherCheck) {
        return weatherCheck == null ? null : weatherCheck.getSummary();
    }
}
