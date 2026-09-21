package com.ondemandmonitoring.user.mapper;

import com.ondemandmonitoring.user.domain.CustomerProfile;
import com.ondemandmonitoring.user.domain.User;
import com.ondemandmonitoring.user.domain.UserIdentity;
import com.ondemandmonitoring.user.dto.response.CustomerProfileResponse;
import com.ondemandmonitoring.user.dto.response.UserManagementDetailResponse;
import com.ondemandmonitoring.user.dto.response.UserManagementSummaryResponse;
import java.util.Comparator;
import java.util.List;
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

    public UserManagementDetailResponse toDetail(
            User user,
            List<UserIdentity> identities,
            CustomerProfile customerProfile) {
        return UserManagementDetailResponse.builder()
                .id(user.getId())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .role(user.getRole().getCode())
                .active(Boolean.TRUE.equals(user.getIsActive()))
                .emailVerified(Boolean.TRUE.equals(user.getEmailVerified()))
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .lastLoginAt(user.getLastLoginAt())
                .linkedProviders(identities.stream()
                        .map(UserIdentity::getProvider)
                        .distinct()
                        .sorted(Comparator.comparing(Enum::name))
                        .toList())
                .customerProfile(toCustomerProfile(customerProfile))
                .build();
    }

    private CustomerProfileResponse toCustomerProfile(CustomerProfile customerProfile) {
        if (customerProfile == null) {
            return null;
        }
        return CustomerProfileResponse.builder()
                .phoneNumber(customerProfile.getPhoneNumber())
                .address(customerProfile.getAddress())
                .companyName(customerProfile.getCompanyName())
                .build();
    }
}
