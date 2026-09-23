package com.ondemandmonitoring.user.dto.response;

import com.ondemandmonitoring.role.domain.RoleCode;
import java.time.OffsetDateTime;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class UserManagementSummaryResponse {

    private String id;

    private String fullName;

    private String email;

    private RoleCode role;

    private boolean active;

    private boolean emailVerified;

    private OffsetDateTime createdAt;

    private OffsetDateTime lastLoginAt;
}
