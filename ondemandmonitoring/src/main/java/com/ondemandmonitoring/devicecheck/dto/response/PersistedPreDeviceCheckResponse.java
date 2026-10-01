package com.ondemandmonitoring.devicecheck.dto.response;

import com.ondemandmonitoring.device.domain.Device;
import com.ondemandmonitoring.devicecheck.domain.PersistedPreDeviceCheck;
import com.ondemandmonitoring.devicecheck.enums.PreDeviceCheckLevel;
import com.ondemandmonitoring.devicecheck.enums.PreDeviceCheckStatus;
import com.ondemandmonitoring.devicecheck.enums.PreDeviceItemStatus;
import com.ondemandmonitoring.mission.domain.DeviceConnection;
import java.time.Instant;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PersistedPreDeviceCheckResponse {

    String id;
    String missionId;
    String deviceConnectionId;
    String deviceId;
    String deviceCode;
    String deviceName;
    String deviceSerialNumber;
    PreDeviceCheckStatus status;
    Integer totalChecks;
    Integer passedChecks;
    Integer failedChecks;
    Instant startedAt;
    Instant completedAt;
    Integer progressPercent;
    List<Item> items;

    @Getter
    @Builder
    public static class Item {

        String checkType;
        String checkName;
        PreDeviceItemStatus status;
        PreDeviceCheckLevel checkLevel;
        String message;
        Instant checkedAt;
    }

    public static PersistedPreDeviceCheckResponse from(PersistedPreDeviceCheck run) {
        DeviceConnection connection = run.getDeviceConnection();
        Device device = connection == null ? null : connection.getDevice();
        int total = run.getTotalChecks() == null ? 0 : run.getTotalChecks();
        int done = run.getItems().stream()
                .filter(i -> i.getStatus() == PreDeviceItemStatus.PASSED
                        || i.getStatus() == PreDeviceItemStatus.FAILED)
                .toList()
                .size();

        return builder()
                .id(run.getId())
                .missionId(run.getMission().getId())
                .deviceConnectionId(connection == null ? null : connection.getId())
                .deviceId(device == null ? null : device.getId())
                .deviceCode(device == null ? null : device.getDeviceCode())
                .deviceName(device == null ? null : device.getName())
                .deviceSerialNumber(device == null ? null : device.getSerialNumber())
                .status(run.getStatus())
                .totalChecks(total)
                .passedChecks(run.getPassedChecks())
                .failedChecks(run.getFailedChecks())
                .startedAt(run.getStartedAt())
                .completedAt(run.getCompletedAt())
                .progressPercent(total == 0 ? 0 : done * 100 / total)
                .items(run.getItems().stream()
                        .map(i -> Item.builder()
                                .checkType(i.getCheckType())
                                .checkName(i.getCheckName())
                                .status(i.getStatus())
                                .checkLevel(i.getCheckLevel())
                                .message(i.getMessage())
                                .checkedAt(i.getCheckedAt())
                                .build())
                        .toList())
                .build();
    }
}
