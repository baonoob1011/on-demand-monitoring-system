package com.ondemandmonitoring.order.dto.response;

import com.ondemandmonitoring.order.enums.OrderDeliveryMethod;
import com.ondemandmonitoring.order.enums.OrderPermitStatus;
import com.ondemandmonitoring.order.enums.OrderPriority;
import com.ondemandmonitoring.order.enums.OrderRecurrenceType;
import com.ondemandmonitoring.order.enums.OrderResultFormat;
import com.ondemandmonitoring.order.enums.OrderStatus;
import com.ondemandmonitoring.order.enums.OrderUsagePurpose;
import com.ondemandmonitoring.order.enums.OrderWeatherFallback;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
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
@Schema(description = "Response object for created monitoring order")
public class OrderCreateResponse {

    String id;
    String orderCode;
    String customerId;
    String customerName;

    // General Info
    String title;
    String serviceId;
    String serviceName;
    String description;
    OrderUsagePurpose usagePurpose;
    OrderPriority priority;
    Double altitudeM;
    Double estimatedLengthM;
    String siteContactName;
    String siteContactPhone;
    String accessNotes;
    OrderPermitStatus permitStatus;
    String permitNumber;
    Boolean permitRequired;
    String permitZoneName;

    // Location Info
    String address;
    Double longitude;
    Double latitude;
    Double radiusM;
    Map<String, Object> coverageArea;

    // Schedule Info
    LocalDate preferredDateFrom;
    LocalDate preferredDateTo;
    String preferredTimeId;
    String preferredTimeName;
    OrderRecurrenceType recurrenceType;
    Integer recurrenceOccurrences;
    OrderWeatherFallback weatherFallback;
    LocalDate resultDeadline;

    // Delivery & terms
    List<OrderResultFormat> resultFormats;
    List<OrderDeliveryMethod> deliveryMethods;
    Integer dataRetentionDays;
    Instant termsAcceptedAt;
    String termsVersion;

    // Status & Review Info
    OrderStatus orderStatus;
    String rejectReason;
    String reviewById;
    String reviewByName;
    Instant reviewAt;

    // Deliverables
    List<OrderDeliverableResponse> deliverables;

    List<OrderChecklistItemResponse> checklistItems;
    Instant checklistSnapshotAt;

    Instant createdAt;
    Instant updatedAt;
}
