package com.ondemandmonitoring.drone.dto.request;

import com.ondemandmonitoring.drone.enums.PreflightItemStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PreflightItemUpdateRequest {

    @NotNull
    PreflightItemStatus status;

    String message;
}
