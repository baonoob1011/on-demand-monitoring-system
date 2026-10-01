package com.ondemandmonitoring.service.dto.response;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

@Getter
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ServiceRequirementSuggestionResponse {
    String id;
    String serviceId;
    String category;
    String label;
    String message;
    Integer sortOrder;
    String source;
}
