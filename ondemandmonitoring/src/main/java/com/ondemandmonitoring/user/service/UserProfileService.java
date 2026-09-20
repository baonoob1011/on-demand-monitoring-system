package com.ondemandmonitoring.user.service;

import com.ondemandmonitoring.common.exception.ApiException;
import com.ondemandmonitoring.common.exception.ErrorCode;
import com.ondemandmonitoring.role.domain.RoleCode;
import com.ondemandmonitoring.user.domain.CustomerProfile;
import com.ondemandmonitoring.user.domain.User;
import com.ondemandmonitoring.user.dto.response.CustomerProfileResponse;
import com.ondemandmonitoring.user.dto.response.UserProfileResponse;
import com.ondemandmonitoring.user.repository.CustomerProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserProfileService {

    private final AuthenticatedUserResolver authenticatedUserResolver;
    private final CustomerProfileRepository customerProfileRepository;

    @Transactional(readOnly = true)
    public UserProfileResponse getCurrentProfile() {
        User user = authenticatedUserResolver.getCurrentUser();
        return toResponse(user, findCustomerProfile(user));
    }

    private CustomerProfile findCustomerProfile(User user) {
        if (user.getRole().getCode() != RoleCode.CUSTOMER) {
            return null;
        }

        return customerProfileRepository.findById(user.getId())
                .orElseThrow(() -> new ApiException(
                        ErrorCode.RESOURCE_NOT_FOUND, "Customer profile not found"));
    }

    private UserProfileResponse toResponse(User user, CustomerProfile customerProfile) {
        return UserProfileResponse.builder()
                .id(user.getId())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .role(user.getRole().getCode())
                .customerProfile(toCustomerResponse(customerProfile))
                .build();
    }

    private CustomerProfileResponse toCustomerResponse(CustomerProfile customerProfile) {
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
