package com.ondemandmonitoring.user.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.ondemandmonitoring.role.domain.RoleCode;
import lombok.*;

import java.util.UUID;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserProfileResponse {

    private UUID id;

    private String fullName;

    private String email;

    private RoleCode role;

    private String avatarUrl;

    private CustomerProfileResponse customerProfile;

}
