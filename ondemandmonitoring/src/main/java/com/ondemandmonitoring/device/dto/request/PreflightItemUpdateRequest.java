package com.ondemandmonitoring.device.dto.request;

import com.ondemandmonitoring.device.enums.PreflightItemStatus;
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
