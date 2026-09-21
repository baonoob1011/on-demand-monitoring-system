package com.ondemandmonitoring.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ondemandmonitoring.role.domain.Role;
import com.ondemandmonitoring.role.domain.RoleCode;
import com.ondemandmonitoring.role.service.RoleService;
import com.ondemandmonitoring.user.domain.CustomerProfile;
import com.ondemandmonitoring.user.domain.User;
import com.ondemandmonitoring.user.enumeration.IdentityProvider;
import com.ondemandmonitoring.user.repository.CustomerProfileRepository;
import com.ondemandmonitoring.user.repository.UserRepository;
import com.ondemandmonitoring.user.service.impl.UserServiceImpl;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleService roleService;

    @Mock
    private IUserIdentityService userIdentityService;

    @Mock
    private CustomerProfileRepository customerProfileRepository;

    @InjectMocks
    private UserServiceImpl userService;

    @Test
    void createLocalUser_customer_provisionsProfile() {
        Role customerRole = role(RoleCode.CUSTOMER);
        when(roleService.getActiveRole(RoleCode.CUSTOMER)).thenReturn(customerRole);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(UUID.randomUUID());
            return user;
        });

        User user = userService.createLocalUser(
                " Customer@Example.com ", "Customer Name", RoleCode.CUSTOMER,
                "cognito-user", "cognito-sub");

        verify(userIdentityService).create(
                user, IdentityProvider.LOCAL, "cognito-user", "cognito-sub");
        ArgumentCaptor<CustomerProfile> profileCaptor =
                ArgumentCaptor.forClass(CustomerProfile.class);
        verify(customerProfileRepository).save(profileCaptor.capture());
        assertThat(profileCaptor.getValue().getUser()).isSameAs(user);
    }

    @Test
    void createSocialUser_customer_provisionsProfile() {
        Role customerRole = role(RoleCode.CUSTOMER);
        when(roleService.getActiveRole(RoleCode.CUSTOMER)).thenReturn(customerRole);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(UUID.randomUUID());
            return user;
        });

        User user = userService.createSocialUser(
                "customer@example.com", "Customer Name", RoleCode.CUSTOMER,
                "google-user", "google-sub");

        verify(userIdentityService).create(
                user, IdentityProvider.GOOGLE, "google-user", "google-sub");
        verify(customerProfileRepository).save(any(CustomerProfile.class));
    }

    @Test
    void createManagedUser_employee_doesNotProvisionCustomerProfile() {
        Role staffRole = role(RoleCode.STAFF);
        when(roleService.getActiveRole(RoleCode.STAFF)).thenReturn(staffRole);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        userService.createManagedUser(
                "staff@example.com", "Staff Name", RoleCode.STAFF,
                "staff-user", "staff-sub");

        verify(customerProfileRepository, never()).save(any(CustomerProfile.class));
    }

    @Test
    void createLocalUser_whenProfileProvisioningFails_propagatesFailure() {
        when(roleService.getActiveRole(RoleCode.CUSTOMER)).thenReturn(role(RoleCode.CUSTOMER));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(customerProfileRepository.save(any(CustomerProfile.class)))
                .thenThrow(new IllegalStateException("profile provisioning failed"));

        assertThatThrownBy(() -> userService.createLocalUser(
                "customer@example.com", "Customer Name", RoleCode.CUSTOMER,
                "cognito-user", "cognito-sub"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("profile provisioning failed");
    }

    @Test
    void findByCognitoUsername_delegatesToIdentityService() {
        User expected = User.builder().id(UUID.randomUUID()).build();
        when(userIdentityService.findUserByCognitoUsername("cognito-user"))
                .thenReturn(expected);

        User actual = userService.findByCognitoUsername("cognito-user");

        assertThat(actual).isSameAs(expected);
    }

    private Role role(RoleCode code) {
        return Role.builder()
                .id(UUID.randomUUID())
                .code(code)
                .name(code.name())
                .active(true)
                .build();
    }
}
