package com.ondemandmonitoring.order.dto.request;

import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

@Getter
@Setter
@NoArgsConstructor
public class OrderChecklistItemRequest {
    @Size(min = 1, max = 255)
    private String sourceChecklistId;
    @PositiveOrZero
    private Long expectedChecklistVersion;
    @Size(max = 500)
    private String contentOverride;
}
