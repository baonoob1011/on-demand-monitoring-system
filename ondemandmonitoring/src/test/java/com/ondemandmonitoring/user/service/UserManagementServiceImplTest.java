package com.ondemandmonitoring.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ondemandmonitoring.auth.infrastructure.outbox.AuthOutboxService;
import com.ondemandmonitoring.common.api.PageResponse;
import com.ondemandmonitoring.common.exception.ApiException;
import com.ondemandmonitoring.common.exception.ErrorCode;
import com.ondemandmonitoring.role.domain.Role;
import com.ondemandmonitoring.role.domain.RoleCode;
import com.ondemandmonitoring.user.domain.User;
import com.ondemandmonitoring.user.domain.CustomerProfile;
import com.ondemandmonitoring.user.domain.UserIdentity;
import com.ondemandmonitoring.user.enumeration.IdentityProvider;
import com.ondemandmonitoring.user.dto.response.UserManagementDetailResponse;
import com.ondemandmonitoring.user.dto.response.UserManagementSummaryResponse;
import com.ondemandmonitoring.user.mapper.UserManagementMapper;
import com.ondemandmonitoring.user.repository.UserRepository;
import com.ondemandmonitoring.user.repository.CustomerProfileRepository;
import com.ondemandmonitoring.user.repository.UserIdentityRepository;
import com.ondemandmonitoring.user.service.impl.UserManagementServiceImpl;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mapstruct.factory.Mappers;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

@ExtendWith(MockitoExtension.class)
class UserManagementServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserIdentityRepository userIdentityRepository;

    @Mock
    private CustomerProfileRepository customerProfileRepository;

    @Mock
    private AuthenticatedUserResolver authenticatedUserResolver;

    @Mock
    private AuthOutboxService authOutboxService;

    private UserManagementServiceImpl userManagementService;

    @BeforeEach
    void setUp() {
        userManagementService = new UserManagementServiceImpl(
                userRepository,
                userIdentityRepository,
                customerProfileRepository,
                Mappers.getMapper(UserManagementMapper.class),
                authenticatedUserResolver,
                authOutboxService);
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

    @Test
    void getUser_customerReturnsProvidersAndCustomerProfile() {
        User user = User.builder()
                .id(UUID.randomUUID())
                .fullName("Customer Name")
                .email("customer@example.com")
                .role(Role.builder().code(RoleCode.CUSTOMER).build())
                .isActive(true)
                .emailVerified(true)
                .build();
        CustomerProfile customerProfile = CustomerProfile.builder()
                .userId(user.getId())
                .user(user)
                .phoneNumber("0901234567")
                .companyName("Customer Company")
                .build();
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(userIdentityRepository.findAllByUserId(user.getId())).thenReturn(List.of(
                UserIdentity.builder().provider(IdentityProvider.GOOGLE).build(),
                UserIdentity.builder().provider(IdentityProvider.LOCAL).build(),
                UserIdentity.builder().provider(IdentityProvider.GOOGLE).build()));
        when(customerProfileRepository.findById(user.getId()))
                .thenReturn(Optional.of(customerProfile));

        UserManagementDetailResponse response = userManagementService.getUser(user.getId());

        assertThat(response.getId()).isEqualTo(user.getId());
        assertThat(response.getLinkedProviders())
                .containsExactly(IdentityProvider.GOOGLE, IdentityProvider.LOCAL);
        assertThat(response.getCustomerProfile().getPhoneNumber()).isEqualTo("0901234567");
    }

    @Test
    void getUser_employeeOmitsCustomerProfile() {
        User user = User.builder()
                .id(UUID.randomUUID())
                .fullName("Staff Name")
                .email("staff@example.com")
                .role(Role.builder().code(RoleCode.STAFF).build())
                .build();
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(userIdentityRepository.findAllByUserId(user.getId())).thenReturn(List.of());

        UserManagementDetailResponse response = userManagementService.getUser(user.getId());

        assertThat(response.getCustomerProfile()).isNull();
        verify(customerProfileRepository, never()).findById(user.getId());
    }

    @Test
    void getUser_unknownUserThrowsUserNotFound() {
        UUID userId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userManagementService.getUser(userId))
                .isInstanceOfSatisfying(ApiException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.USER_NOT_FOUND));
    }

    @Test
    void updateStatus_changesLocalStatusAndSchedulesUniqueIdentitySync() {
        User actor = adminUser(UUID.randomUUID(), true);
        User target = customerUser(UUID.randomUUID(), true);
        List<UserIdentity> identities = List.of(
                identity("local-user"),
                identity("google-user"),
                identity("google-user"));
        when(authenticatedUserResolver.getCurrentUser()).thenReturn(actor);
        when(userRepository.findById(target.getId())).thenReturn(Optional.of(target));
        when(userIdentityRepository.findAllByUserId(target.getId())).thenReturn(identities);
        when(customerProfileRepository.findById(target.getId())).thenReturn(Optional.empty());

        UserManagementDetailResponse response = userManagementService.updateStatus(
                target.getId(), false);

        assertThat(response.isActive()).isFalse();
        assertThat(target.getIsActive()).isFalse();
        verify(userRepository).save(target);
        verify(authOutboxService).scheduleAccountStatusSync("local-user", false);
        verify(authOutboxService).scheduleAccountStatusSync("google-user", false);
    }

    @Test
    void updateStatus_sameStatusIsIdempotent() {
        User actor = adminUser(UUID.randomUUID(), true);
        User target = customerUser(UUID.randomUUID(), false);
        when(authenticatedUserResolver.getCurrentUser()).thenReturn(actor);
        when(userRepository.findById(target.getId())).thenReturn(Optional.of(target));
        when(userIdentityRepository.findAllByUserId(target.getId())).thenReturn(List.of());
        when(customerProfileRepository.findById(target.getId())).thenReturn(Optional.empty());

        UserManagementDetailResponse response = userManagementService.updateStatus(
                target.getId(), false);

        assertThat(response.isActive()).isFalse();
        verify(userRepository, never()).save(target);
        verify(authOutboxService, never()).scheduleAccountStatusSync(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyBoolean());
    }

    @Test
    void updateStatus_rejectsSelfDeactivation() {
        UUID adminId = UUID.randomUUID();
        User actor = adminUser(adminId, true);
        when(authenticatedUserResolver.getCurrentUser()).thenReturn(actor);
        when(userRepository.findById(adminId)).thenReturn(Optional.of(actor));

        assertThatThrownBy(() -> userManagementService.updateStatus(adminId, false))
                .isInstanceOfSatisfying(ApiException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.SELF_DEACTIVATION_NOT_ALLOWED));
        verify(userRepository, never()).save(actor);
    }

    @Test
    void updateStatus_unknownTargetThrowsUserNotFound() {
        UUID userId = UUID.randomUUID();
        when(authenticatedUserResolver.getCurrentUser())
                .thenReturn(adminUser(UUID.randomUUID(), true));
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userManagementService.updateStatus(userId, false))
                .isInstanceOfSatisfying(ApiException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.USER_NOT_FOUND));
    }

    private User adminUser(UUID id, boolean active) {
        return User.builder()
                .id(id)
                .fullName("Admin")
                .email("admin@example.com")
                .role(Role.builder().code(RoleCode.ADMIN).build())
                .isActive(active)
                .build();
    }

    private User customerUser(UUID id, boolean active) {
        return User.builder()
                .id(id)
                .fullName("Customer")
                .email("customer@example.com")
                .role(Role.builder().code(RoleCode.CUSTOMER).build())
                .isActive(active)
                .emailVerified(true)
                .build();
    }

    private UserIdentity identity(String username) {
        return UserIdentity.builder()
                .provider(IdentityProvider.LOCAL)
                .cognitoUsername(username)
                .cognitoSub(UUID.randomUUID().toString())
                .build();
    }
}
