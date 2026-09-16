package com.ondemandmonitoring.warehouse.dto.request;

import com.ondemandmonitoring.warehouse.enums.PreferredTimeCode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
public class PreferredTimeCreateRequest {

    @NotNull(message = "Code is required")
    PreferredTimeCode code;

    @NotBlank(message = "Name is required")
    String name;

    @NotNull(message = "Start time is required")
    LocalTime startTime;

    @NotNull(message = "End time is required")
    LocalTime endTime;

}
