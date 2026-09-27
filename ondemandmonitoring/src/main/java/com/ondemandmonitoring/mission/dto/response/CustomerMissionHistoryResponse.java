package com.ondemandmonitoring.mission.dto.response;

import com.ondemandmonitoring.mission.enums.MissionStatus;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CustomerMissionHistoryResponse {
    String id;
    String missionCode;
    String orderId;
    String orderTitle;
    String address;
    MissionStatus status;
    Instant scheduledStartAt;
    Instant startedAt;
    Instant completedAt;
    String description;
}
