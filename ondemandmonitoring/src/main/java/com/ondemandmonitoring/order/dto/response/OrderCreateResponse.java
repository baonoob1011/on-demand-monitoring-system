package com.ondemandmonitoring.order.dto.response;

import com.ondemandmonitoring.order.enums.MediaTypeSp;
import com.ondemandmonitoring.order.enums.OrderStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
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
public class OrderCreateResponse {

    Long id;
    Long customerId;

    // General Info
    String title;
    Long categoryServiceId;
    String purpose;
    String description;

    // Location Info
    double longitude;
    double latitude;
    String address;

    // Schedule Info
    LocalDate startDate;
    LocalTime startTime;
    int durationHour;

    // Media Info
    MediaTypeSp mediaType;

    OrderStatus orderStatus;
    LocalDateTime createdAt;
    LocalDateTime updatedAt;
}
