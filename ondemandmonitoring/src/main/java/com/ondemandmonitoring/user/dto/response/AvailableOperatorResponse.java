package com.ondemandmonitoring.user.dto.response;

import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

@Getter
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AvailableOperatorResponse {

    UUID id;
    String fullName;
    String email;
}
