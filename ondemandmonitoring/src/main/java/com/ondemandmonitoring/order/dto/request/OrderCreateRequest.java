package com.ondemandmonitoring.order.dto.request;

import com.ondemandmonitoring.order.enums.MediaTypeSp;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
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
public class OrderCreateRequest {

    @NotNull(message = "Customer ID is required")
    Long customerId;

    // General Info
    @NotBlank(message = "Title is required")
    String title;

    @NotNull(message = "Category Service ID is required")
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
}
