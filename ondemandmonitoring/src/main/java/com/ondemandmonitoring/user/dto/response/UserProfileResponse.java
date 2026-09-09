package com.ondemandmonitoring.user.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.ondemandmonitoring.user.enumeration.UserRole;
import lombok.*;

import java.util.List;
import java.util.UUID;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserProfileResponse {

    private UUID id;

    private String fullName;

    private String email;

    private Boolean emailVerified;

    private UserRole role;

    private List<String> linkedProviders;

    private String avatarUrl;

    private Boolean isActive;

}
