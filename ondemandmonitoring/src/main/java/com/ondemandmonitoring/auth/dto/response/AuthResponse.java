package com.ondemandmonitoring.auth.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.ondemandmonitoring.user.dto.response.UserProfileResponse;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AuthResponse {

    private String accessToken;

    private String status;

    private String challengeName;

    private String session;

    @Builder.Default
    private String tokenType = "Bearer";

    private Integer expiresIn;

    private UserProfileResponse user;

}
