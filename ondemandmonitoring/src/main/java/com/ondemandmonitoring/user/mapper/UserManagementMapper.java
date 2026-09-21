package com.ondemandmonitoring.user.mapper;

import com.ondemandmonitoring.user.domain.User;
import com.ondemandmonitoring.user.dto.response.UserManagementSummaryResponse;
import org.springframework.stereotype.Component;

@Component
public class UserManagementMapper {

    public UserManagementSummaryResponse toSummary(User user) {
        return UserManagementSummaryResponse.builder()
                .id(user.getId())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .role(user.getRole().getCode())
                .active(Boolean.TRUE.equals(user.getIsActive()))
                .emailVerified(Boolean.TRUE.equals(user.getEmailVerified()))
                .createdAt(user.getCreatedAt())
                .lastLoginAt(user.getLastLoginAt())
                .build();
    }
}
