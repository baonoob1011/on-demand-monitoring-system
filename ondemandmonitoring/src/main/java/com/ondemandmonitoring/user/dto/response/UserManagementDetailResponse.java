package com.ondemandmonitoring.user.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.ondemandmonitoring.role.domain.RoleCode;
import com.ondemandmonitoring.user.enumeration.IdentityProvider;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserManagementDetailResponse {

    private UUID id;

    private String fullName;

    private String email;

    private RoleCode role;

    private boolean active;

    private boolean emailVerified;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;

    private OffsetDateTime lastLoginAt;

    private List<IdentityProvider> linkedProviders;

    private CustomerProfileResponse customerProfile;
}
