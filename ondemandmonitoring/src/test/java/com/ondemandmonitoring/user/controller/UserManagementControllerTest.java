package com.ondemandmonitoring.user.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ondemandmonitoring.common.api.PageResponse;
import com.ondemandmonitoring.role.domain.RoleCode;
import com.ondemandmonitoring.user.dto.response.UserManagementSummaryResponse;
import com.ondemandmonitoring.user.dto.response.UserManagementDetailResponse;
import com.ondemandmonitoring.user.dto.response.CustomerProfileResponse;
import com.ondemandmonitoring.user.enumeration.IdentityProvider;
import com.ondemandmonitoring.user.service.IUserManagementService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class UserManagementControllerTest {

    private IUserManagementService userManagementService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        userManagementService = mock(IUserManagementService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new UserManagementController(userManagementService))
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .build();
    }

    @Test
    void controllerRequiresAdminRole() {
        PreAuthorize authorization = UserManagementController.class
                .getAnnotation(PreAuthorize.class);

        assertThat(authorization).isNotNull();
        assertThat(authorization.value()).isEqualTo("hasRole('ADMIN')");
    }

    @Test
    void getUsers_returnsFilteredPage() throws Exception {
        UUID userId = UUID.randomUUID();
        PageResponse<UserManagementSummaryResponse> page = PageResponse.<UserManagementSummaryResponse>builder()
                .items(List.of(UserManagementSummaryResponse.builder()
                        .id(userId)
                        .fullName("Customer Name")
                        .email("customer@example.com")
                        .role(RoleCode.CUSTOMER)
                        .active(true)
                        .emailVerified(true)
                        .build()))
                .page(0)
                .size(20)
                .totalItems(1)
                .totalPages(1)
                .first(true)
                .last(true)
                .build();
        when(userManagementService.getUsers(
                any(Pageable.class),
                eq("customer"),
                eq(RoleCode.CUSTOMER),
                eq(true),
                eq(true)))
                .thenReturn(page);

        mockMvc.perform(get("/api/v1/admin/users")
                        .param("search", "customer")
                        .param("role", "CUSTOMER")
                        .param("active", "true")
                        .param("emailVerified", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.items[0].id").value(userId.toString()))
                .andExpect(jsonPath("$.data.items[0].active").value(true))
                .andExpect(jsonPath("$.data.totalItems").value(1));
    }

    @Test
    void getUser_returnsManagementDetail() throws Exception {
        UUID userId = UUID.randomUUID();
        when(userManagementService.getUser(userId)).thenReturn(
                UserManagementDetailResponse.builder()
                        .id(userId)
                        .fullName("Customer Name")
                        .email("customer@example.com")
                        .role(RoleCode.CUSTOMER)
                        .active(true)
                        .emailVerified(true)
                        .linkedProviders(List.of(IdentityProvider.LOCAL, IdentityProvider.GOOGLE))
                        .customerProfile(CustomerProfileResponse.builder()
                                .companyName("Customer Company")
                                .build())
                        .build());

        mockMvc.perform(get("/api/v1/admin/users/{userId}", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(userId.toString()))
                .andExpect(jsonPath("$.data.linkedProviders[0]").value("LOCAL"))
                .andExpect(jsonPath("$.data.customerProfile.companyName")
                        .value("Customer Company"));
    }
}
