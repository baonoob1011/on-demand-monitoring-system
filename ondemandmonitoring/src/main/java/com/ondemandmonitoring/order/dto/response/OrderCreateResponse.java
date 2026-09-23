package com.ondemandmonitoring.order.dto.response;

import com.ondemandmonitoring.order.enums.OrderStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
@Schema(description = "Response object for created monitoring order")
public class OrderCreateResponse {

    String id;
    UUID customerId;
    String customerName;

    // General Info
    String title;
    String serviceId;
    String serviceName;
    String description;

    // Location Info
    String address;
    Double longitude;
    Double latitude;
    Map<String, Object> coverageArea;

    // Schedule Info
    LocalDate preferredDateFrom;
    LocalDate preferredDateTo;
    String preferredTimeId;
    String preferredTimeName;

    // Status & Review Info
    OrderStatus orderStatus;
    String rejectReason;
    UUID reviewById;
    String reviewByName;
    Instant reviewAt;

    // Deliverables
    List<OrderDeliverableResponse> deliverables;

    Instant createdAt;
    Instant updatedAt;
}
