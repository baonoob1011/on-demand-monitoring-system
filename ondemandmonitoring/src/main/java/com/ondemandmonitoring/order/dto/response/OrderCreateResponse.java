package com.ondemandmonitoring.order.dto.response;

import com.ondemandmonitoring.order.dto.GeoJsonPointDto;
import com.ondemandmonitoring.order.enums.MediaTypeSp;
import com.ondemandmonitoring.order.enums.OrderStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.LocalDate;
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
    String purpose;
    String description;

    // Location Info
    String address;
    GeoJsonPointDto point;

    // Schedule Info
    LocalDate preferredDate;
    String preferredTimeId;
    String preferredTimeName;

    // Media Info
    MediaTypeSp mediaType;
    Integer durationOfVideo;
    Integer numberOfPhoto;

    OrderStatus orderStatus;
    Instant createdAt;
    Instant updatedAt;
}
