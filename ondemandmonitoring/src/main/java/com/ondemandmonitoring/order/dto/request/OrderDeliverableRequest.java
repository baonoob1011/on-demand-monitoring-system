package com.ondemandmonitoring.order.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Map;
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
@Schema(description = "Request object for order deliverable requirement")
public class OrderDeliverableRequest {

    @NotBlank(message = "Deliverable type ID is required")
    @Schema(description = "Deliverable Type ID", example = "uuid-123")
    String deliverableTypeId;

    @NotNull(message = "Requirement is required")
    @Schema(description = "Requirement parameters map", example = "{\"resolution\": \"4K\", \"format\": \"MP4\"}")
    Map<String, Object> requirement;
}
