package com.ondemandmonitoring.user.service.impl;

import com.ondemandmonitoring.common.exception.ApiException;
import com.ondemandmonitoring.common.exception.ErrorCode;
import com.ondemandmonitoring.role.domain.RoleCode;
import com.ondemandmonitoring.user.domain.CustomerProfile;
import com.ondemandmonitoring.user.domain.User;
import com.ondemandmonitoring.user.dto.request.UserProfileUpdateRequest;
import com.ondemandmonitoring.user.dto.response.UserProfileResponse;
import com.ondemandmonitoring.user.mapper.UserProfileMapper;
import com.ondemandmonitoring.user.repository.CustomerProfileRepository;
import com.ondemandmonitoring.user.repository.UserRepository;
import com.ondemandmonitoring.user.service.AuthenticatedUserResolver;
import com.ondemandmonitoring.user.service.IUserProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserProfileServiceImpl implements IUserProfileService {

    private final AuthenticatedUserResolver authenticatedUserResolver;
    private final CustomerProfileRepository customerProfileRepository;
    private final UserRepository userRepository;
    private final UserProfileMapper userProfileMapper;

    @Override
    @Transactional(readOnly = true)
    public UserProfileResponse getCurrentProfile() {
        User user = authenticatedUserResolver.getCurrentUser();
        return userProfileMapper.toResponse(user, findCustomerProfile(user));
    }

    @Override
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

        return userProfileMapper.toResponse(user, customerProfile);
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

    private void applyCustomerUpdates(CustomerProfile customerProfile, UserProfileUpdateRequest request) {
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
}
