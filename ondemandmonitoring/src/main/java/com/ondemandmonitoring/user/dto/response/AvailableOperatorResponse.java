package com.ondemandmonitoring.user.dto.response;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

@Getter
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AvailableOperatorResponse {

    String id;
    String fullName;
    String email;
}
