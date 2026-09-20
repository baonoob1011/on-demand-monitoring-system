package com.ondemandmonitoring.user.service;

import com.ondemandmonitoring.common.exception.ApiException;
import com.ondemandmonitoring.common.exception.ErrorCode;
import com.ondemandmonitoring.role.domain.RoleCode;
import com.ondemandmonitoring.user.domain.CustomerProfile;
import com.ondemandmonitoring.user.domain.User;
import com.ondemandmonitoring.user.dto.request.UserProfileUpdateRequest;
import com.ondemandmonitoring.user.dto.response.CustomerProfileResponse;
import com.ondemandmonitoring.user.dto.response.UserProfileResponse;
import com.ondemandmonitoring.user.repository.CustomerProfileRepository;
import com.ondemandmonitoring.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserProfileService {

    private final AuthenticatedUserResolver authenticatedUserResolver;
    private final CustomerProfileRepository customerProfileRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public UserProfileResponse getCurrentProfile() {
        User user = authenticatedUserResolver.getCurrentUser();
        return toResponse(user, findCustomerProfile(user));
    }

    @Transactional
    public UserProfileResponse updateCurrentProfile(UserProfileUpdateRequest request) {
        validateUpdateRequest(request);

        User user = authenticatedUserResolver.getCurrentUser();
        boolean customer = user.getRole().getCode() == RoleCode.CUSTOMER;
        if (!customer && containsCustomerFields(request)) {
            throw new ApiException(
                    ErrorCode.INVALID_REQUEST,
                    "Customer profile fields are only available to customers");
        }

        CustomerProfile customerProfile = customer ? findCustomerProfile(user) : null;
        if (request.getFullName() != null) {
            user.setFullName(request.getFullName().trim());
            userRepository.save(user);
        }
        if (customerProfile != null && containsCustomerFields(request)) {
            applyCustomerUpdates(customerProfile, request);
            customerProfileRepository.save(customerProfile);
        }

        return toResponse(user, customerProfile);
    }

    private void validateUpdateRequest(UserProfileUpdateRequest request) {
        if (request.getFullName() == null
                && request.getPhoneNumber() == null
                && request.getAddress() == null
                && request.getCompanyName() == null) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "At least one profile field is required");
        }
    }

    private boolean containsCustomerFields(UserProfileUpdateRequest request) {
        return request.getPhoneNumber() != null
                || request.getAddress() != null
                || request.getCompanyName() != null;
    }

    private void applyCustomerUpdates(
            CustomerProfile customerProfile,
            UserProfileUpdateRequest request) {
        if (request.getPhoneNumber() != null) {
            customerProfile.setPhoneNumber(trimToNull(request.getPhoneNumber()));
        }
        if (request.getAddress() != null) {
            customerProfile.setAddress(trimToNull(request.getAddress()));
        }
        if (request.getCompanyName() != null) {
            customerProfile.setCompanyName(trimToNull(request.getCompanyName()));
        }
    }

    private String trimToNull(String value) {
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
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
