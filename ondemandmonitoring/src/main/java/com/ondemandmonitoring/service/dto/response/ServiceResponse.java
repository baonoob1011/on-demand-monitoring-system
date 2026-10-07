package com.ondemandmonitoring.service.dto.response;

import java.time.Instant;
import java.math.BigDecimal;
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
public class ServiceResponse {

    String id;
    String name;
    String description;
    BigDecimal basePrice;
    String imageUrl;
    Boolean isActive;
    Instant createdAt;
    Instant updatedAt;
}
