package com.ondemandmonitoring.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ondemandmonitoring.common.exception.ApiException;
import com.ondemandmonitoring.common.exception.ErrorCode;
import com.ondemandmonitoring.role.domain.Role;
import com.ondemandmonitoring.role.domain.RoleCode;
import com.ondemandmonitoring.user.domain.CustomerProfile;
import com.ondemandmonitoring.user.domain.User;
import com.ondemandmonitoring.user.dto.response.UserProfileResponse;
import com.ondemandmonitoring.user.repository.CustomerProfileRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserProfileServiceTest {

    @Mock
    private AuthenticatedUserResolver authenticatedUserResolver;

    @Mock
    private CustomerProfileRepository customerProfileRepository;

    @InjectMocks
    private UserProfileService userProfileService;

    @Test
    void getCurrentProfile_customer_returnsCommonAndCustomerFields() {
        User user = user(RoleCode.CUSTOMER);
        CustomerProfile customerProfile = CustomerProfile.builder()
                .userId(user.getId())
                .user(user)
                .phoneNumber("0901234567")
                .address("Ho Chi Minh City")
                .companyName("ODMS Customer")
                .build();
        when(authenticatedUserResolver.getCurrentUser()).thenReturn(user);
        when(customerProfileRepository.findById(user.getId()))
                .thenReturn(Optional.of(customerProfile));

        UserProfileResponse response = userProfileService.getCurrentProfile();

        assertThat(response.getId()).isEqualTo(user.getId());
        assertThat(response.getEmail()).isEqualTo(user.getEmail());
        assertThat(response.getFullName()).isEqualTo(user.getFullName());
        assertThat(response.getRole()).isEqualTo(RoleCode.CUSTOMER);
        assertThat(response.getCustomerProfile().getPhoneNumber()).isEqualTo("0901234567");
        assertThat(response.getCustomerProfile().getAddress()).isEqualTo("Ho Chi Minh City");
        assertThat(response.getCustomerProfile().getCompanyName()).isEqualTo("ODMS Customer");
        assertThat(response.getEmailVerified()).isNull();
        assertThat(response.getIsActive()).isNull();
        assertThat(response.getLinkedProviders()).isNull();
    }

    @Test
    void getCurrentProfile_employee_returnsOnlyCommonFields() {
        User user = user(RoleCode.STAFF);
        when(authenticatedUserResolver.getCurrentUser()).thenReturn(user);

        UserProfileResponse response = userProfileService.getCurrentProfile();

        assertThat(response.getRole()).isEqualTo(RoleCode.STAFF);
        assertThat(response.getCustomerProfile()).isNull();
        verify(customerProfileRepository, never()).findById(user.getId());
    }

    @Test
    void getCurrentProfile_customerWithoutProfile_throwsResourceNotFound() {
        User user = user(RoleCode.CUSTOMER);
        when(authenticatedUserResolver.getCurrentUser()).thenReturn(user);
        when(customerProfileRepository.findById(user.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(userProfileService::getCurrentProfile)
                .isInstanceOfSatisfying(ApiException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
    }

    private User user(RoleCode roleCode) {
        return User.builder()
                .id(UUID.randomUUID())
                .email("user@example.com")
                .fullName("User Name")
                .role(Role.builder().code(roleCode).build())
                .build();
    }
}
