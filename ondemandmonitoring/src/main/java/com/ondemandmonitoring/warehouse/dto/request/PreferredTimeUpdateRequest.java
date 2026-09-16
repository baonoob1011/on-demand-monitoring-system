package com.ondemandmonitoring.warehouse.dto.request;

import com.ondemandmonitoring.warehouse.enums.PreferredTimeCode;
import java.time.LocalTime;
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
public class PreferredTimeUpdateRequest {

    PreferredTimeCode code;
    String name;
    LocalTime startTime;
    LocalTime endTime;
}
