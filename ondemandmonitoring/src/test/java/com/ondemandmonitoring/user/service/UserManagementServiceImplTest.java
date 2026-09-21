package com.ondemandmonitoring.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ondemandmonitoring.common.api.PageResponse;
import com.ondemandmonitoring.common.exception.ApiException;
import com.ondemandmonitoring.common.exception.ErrorCode;
import com.ondemandmonitoring.role.domain.Role;
import com.ondemandmonitoring.role.domain.RoleCode;
import com.ondemandmonitoring.user.domain.User;
import com.ondemandmonitoring.user.dto.response.UserManagementSummaryResponse;
import com.ondemandmonitoring.user.mapper.UserManagementMapper;
import com.ondemandmonitoring.user.repository.UserRepository;
import com.ondemandmonitoring.user.service.impl.UserManagementServiceImpl;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

@ExtendWith(MockitoExtension.class)
class UserManagementServiceImplTest {

    @Mock
    private UserRepository userRepository;

    private UserManagementServiceImpl userManagementService;

    @BeforeEach
    void setUp() {
        userManagementService = new UserManagementServiceImpl(
                userRepository, new UserManagementMapper());
    }

    @Test
    void getUsers_normalizesFiltersAndMapsPage() {
        Pageable pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt"));
        User user = User.builder()
                .id(UUID.randomUUID())
                .fullName("Customer Name")
                .email("customer@example.com")
                .role(Role.builder().code(RoleCode.CUSTOMER).build())
                .isActive(true)
                .emailVerified(true)
                .createdAt(OffsetDateTime.now())
                .build();
        when(userRepository.findForManagement(
                "customer", RoleCode.CUSTOMER, true, true, pageable))
                .thenReturn(new PageImpl<>(List.of(user), pageable, 1));

        PageResponse<UserManagementSummaryResponse> response = userManagementService.getUsers(
                pageable, "  customer  ", RoleCode.CUSTOMER, true, true);

        assertThat(response.getItems()).hasSize(1);
        assertThat(response.getItems().getFirst().getId()).isEqualTo(user.getId());
        assertThat(response.getItems().getFirst().getRole()).isEqualTo(RoleCode.CUSTOMER);
        assertThat(response.getItems().getFirst().isActive()).isTrue();
        assertThat(response.getTotalItems()).isEqualTo(1);
    }

    @Test
    void getUsers_blankSearchBecomesNull() {
        Pageable pageable = PageRequest.of(0, 20);
        when(userRepository.findForManagement(null, null, null, null, pageable))
                .thenReturn(new PageImpl<>(List.of(), pageable, 0));

        userManagementService.getUsers(pageable, "   ", null, null, null);

        verify(userRepository).findForManagement(null, null, null, null, pageable);
    }

    @Test
    void getUsers_rejectsUnsupportedSortProperty() {
        Pageable pageable = PageRequest.of(0, 20, Sort.by("password"));

        assertThatThrownBy(() -> userManagementService.getUsers(
                pageable, null, null, null, null))
                .isInstanceOfSatisfying(ApiException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.INVALID_REQUEST));
        verify(userRepository, never()).findForManagement(
                null, null, null, null, pageable);
    }

    @Test
    void getUsers_rejectsPageSizeAboveLimit() {
        Pageable pageable = PageRequest.of(0, 101);

        assertThatThrownBy(() -> userManagementService.getUsers(
                pageable, null, null, null, null))
                .isInstanceOfSatisfying(ApiException.class,
                        exception -> assertThat(exception.getMessage())
                                .contains("must not exceed 100"));
    }
}
