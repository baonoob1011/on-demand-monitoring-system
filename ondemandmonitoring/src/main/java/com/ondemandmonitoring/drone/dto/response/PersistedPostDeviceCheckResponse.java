package com.ondemandmonitoring.drone.dto.response;

import com.ondemandmonitoring.drone.domain.PersistedPostDeviceCheck;
import com.ondemandmonitoring.drone.enums.DeviceCheckItemStatus;
import com.ondemandmonitoring.drone.enums.DeviceCheckLevel;
import com.ondemandmonitoring.drone.enums.DeviceCheckStatus;
import java.time.Instant;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PersistedPostDeviceCheckResponse {

    String id;
    String missionId;
    String deviceConnectionId;
    DeviceCheckStatus status;
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
        DeviceCheckItemStatus status;
        DeviceCheckLevel checkLevel;
        String message;
        Instant checkedAt;
    }

    public static PersistedPostDeviceCheckResponse from(PersistedPostDeviceCheck run) {
        int total = run.getTotalChecks() == null ? 0 : run.getTotalChecks();
        int done = run.getItems().stream()
                .filter(i -> i.getStatus() == DeviceCheckItemStatus.PASSED
                        || i.getStatus() == DeviceCheckItemStatus.FAILED)
                .toList()
                .size();

        return builder()
                .id(run.getId())
                .missionId(run.getMission().getId())
                .deviceConnectionId(run.getDeviceConnection().getId())
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

