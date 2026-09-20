package com.ondemandmonitoring.auth.mapper;

import com.ondemandmonitoring.auth.dto.response.AuthenticatedUserResponse;
import com.ondemandmonitoring.user.domain.User;
import org.springframework.stereotype.Component;

@Component
public class AuthenticatedUserMapper {

    public AuthenticatedUserResponse toResponse(User user) {
        return AuthenticatedUserResponse.builder()
                .id(user.getId())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .role(user.getRole().getCode())
                .build();
    }
}
