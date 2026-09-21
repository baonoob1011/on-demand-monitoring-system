package com.ondemandmonitoring.user.service.impl;

import com.ondemandmonitoring.common.api.PageResponse;
import com.ondemandmonitoring.common.exception.ApiException;
import com.ondemandmonitoring.common.exception.ErrorCode;
import com.ondemandmonitoring.role.domain.RoleCode;
import com.ondemandmonitoring.user.domain.User;
import com.ondemandmonitoring.user.domain.CustomerProfile;
import com.ondemandmonitoring.user.dto.response.UserManagementDetailResponse;
import com.ondemandmonitoring.user.dto.response.UserManagementSummaryResponse;
import com.ondemandmonitoring.user.mapper.UserManagementMapper;
import com.ondemandmonitoring.user.repository.CustomerProfileRepository;
import com.ondemandmonitoring.user.repository.UserIdentityRepository;
import com.ondemandmonitoring.user.repository.UserRepository;
import com.ondemandmonitoring.user.service.IUserManagementService;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserManagementServiceImpl implements IUserManagementService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final Set<String> ALLOWED_SORT_PROPERTIES = Set.of(
            "id",
            "fullName",
            "email",
            "role.code",
            "isActive",
            "emailVerified",
            "createdAt",
            "lastLoginAt");

    private final UserRepository userRepository;
    private final UserIdentityRepository userIdentityRepository;
    private final CustomerProfileRepository customerProfileRepository;
    private final UserManagementMapper userManagementMapper;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<UserManagementSummaryResponse> getUsers(
            Pageable pageable,
            String search,
            RoleCode role,
            Boolean active,
            Boolean emailVerified) {
        validatePageable(pageable);
        Page<User> users = userRepository.findForManagement(
                normalizeSearch(search), role, active, emailVerified, pageable);
        return PageResponse.from(users.map(userManagementMapper::toSummary));
    }

    @Override
    @Transactional(readOnly = true)
    public UserManagementDetailResponse getUser(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND));
        CustomerProfile customerProfile = null;
        if (user.getRole().getCode() == RoleCode.CUSTOMER) {
            customerProfile = customerProfileRepository.findById(userId).orElse(null);
        }
        return userManagementMapper.toDetail(
                user,
                userIdentityRepository.findAllByUserId(userId),
                customerProfile);
    }

    private void validatePageable(Pageable pageable) {
        if (pageable.getPageSize() > MAX_PAGE_SIZE) {
            throw new ApiException(
                    ErrorCode.INVALID_REQUEST,
                    "Page size must not exceed " + MAX_PAGE_SIZE);
        }
        pageable.getSort().forEach(order -> {
            if (!ALLOWED_SORT_PROPERTIES.contains(order.getProperty())) {
                throw new ApiException(
                        ErrorCode.INVALID_REQUEST,
                        "Unsupported user sort property: " + order.getProperty());
            }
        });
    }

    private String normalizeSearch(String search) {
        if (search == null || search.isBlank()) {
            return null;
        }
        return search.trim();
    }
}
