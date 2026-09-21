package com.ondemandmonitoring.drone.dto.response;

import com.ondemandmonitoring.drone.domain.PersistedPreflightCheck;
import com.ondemandmonitoring.drone.enums.PreflightCheckLevel;
import com.ondemandmonitoring.drone.enums.PreflightCheckStatus;
import com.ondemandmonitoring.drone.enums.PreflightItemStatus;
import java.time.Instant;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PersistedPreflightCheckResponse {

    String id;
    String missionId;
    PreflightCheckStatus status;
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
        PreflightItemStatus status;
        PreflightCheckLevel checkLevel;
        String message;
        Instant checkedAt;
    }

    public static PersistedPreflightCheckResponse from(PersistedPreflightCheck run) {
        int total = run.getTotalChecks() == null ? 0 : run.getTotalChecks();
        int done = run.getItems().stream()
                .filter(i -> i.getStatus() == PreflightItemStatus.PASSED
                        || i.getStatus() == PreflightItemStatus.FAILED)
                .toList()
                .size();

        return builder()
                .id(run.getId())
                .missionId(run.getMission().getId())
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
