package com.ondemandmonitoring.user.service;

import com.ondemandmonitoring.common.api.PageResponse;
import com.ondemandmonitoring.role.domain.RoleCode;
import com.ondemandmonitoring.user.dto.response.UserManagementSummaryResponse;
import org.springframework.data.domain.Pageable;

public interface IUserManagementService {

    PageResponse<UserManagementSummaryResponse> getUsers(
            Pageable pageable,
            String search,
            RoleCode role,
            Boolean active,
            Boolean emailVerified);
}
