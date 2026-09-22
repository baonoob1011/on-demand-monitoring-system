package com.ondemandmonitoring.user.dto.response;

import com.ondemandmonitoring.role.domain.RoleCode;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UserManagementSummaryResponse {

    UUID id;
    String fullName;
    String email;
    RoleCode role;
    boolean active;
    boolean emailVerified;
    OffsetDateTime createdAt;
    OffsetDateTime lastLoginAt;
}
