package com.ondemandmonitoring.user.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.ondemandmonitoring.role.domain.RoleCode;
import com.ondemandmonitoring.user.enumeration.IdentityProvider;
import java.time.OffsetDateTime;
import java.util.List;
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
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserManagementDetailResponse {

    UUID id;
    String fullName;
    String email;
    RoleCode role;
    boolean active;
    boolean emailVerified;
    OffsetDateTime createdAt;
    OffsetDateTime updatedAt;
    OffsetDateTime lastLoginAt;
    List<IdentityProvider> linkedProviders;
    CustomerProfileResponse customerProfile;
}
