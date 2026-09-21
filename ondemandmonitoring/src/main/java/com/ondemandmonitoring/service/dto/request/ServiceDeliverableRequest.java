package com.ondemandmonitoring.service.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ServiceDeliverableRequest {

    @NotBlank(message = "Service ID is required")
    String serviceId;

    @NotBlank(message = "Deliverable type ID is required")
    String deliverableTypeId;
}
