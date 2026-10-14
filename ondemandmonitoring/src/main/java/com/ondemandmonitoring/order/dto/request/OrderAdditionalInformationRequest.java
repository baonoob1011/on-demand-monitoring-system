package com.ondemandmonitoring.order.dto.request;

import com.ondemandmonitoring.order.enums.MonitoringRelationship;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record OrderAdditionalInformationRequest(
        @NotBlank(message = "Additional information is required")
        @Size(max = 2000, message = "Additional information must not exceed 2000 characters")
        String information,
        @Size(max = 120, message = "Contact name must not exceed 120 characters")
        String siteContactName,
        @Pattern(regexp = "^$|^[+0-9 ().-]{8,20}$", message = "Invalid contact phone number")
        String siteContactPhone,
        @Size(max = 1000, message = "Access notes must not exceed 1000 characters")
        String accessNotes,
        MonitoringRelationship monitoringRelationship,
        Boolean monitoringAuthorizationConfirmed) {
}
