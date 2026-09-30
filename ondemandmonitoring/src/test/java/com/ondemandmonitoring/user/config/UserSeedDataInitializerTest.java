package com.ondemandmonitoring.user.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import com.ondemandmonitoring.role.domain.Role;
import com.ondemandmonitoring.role.repository.RoleRepository;
import com.ondemandmonitoring.user.domain.CustomerProfile;
import com.ondemandmonitoring.user.domain.User;
import com.ondemandmonitoring.user.repository.CustomerProfileRepository;
import com.ondemandmonitoring.user.repository.UserRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

@ExtendWith(MockitoExtension.class)
class UserSeedDataInitializerTest {

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private UserRepository userRepository;

    @Mock
    private CustomerProfileRepository customerProfileRepository;

    @Test
    void run_provisionsOnlyMissingCustomerProfile() throws Exception {
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class))).thenReturn(0);
        when(roleRepository.findByCode(any())).thenAnswer(invocation -> Optional.of(
                Role.builder()
                        .id(UUID.randomUUID())
                        .code(invocation.getArgument(0))
                        .name(invocation.getArgument(0).toString())
                        .active(true)
                        .build()));
        when(userRepository.findByEmailIgnoreCase(anyString())).thenAnswer(invocation -> {
            String email = invocation.getArgument(0);
            User customer = User.builder()
                    .email(email)
                    .fullName("Seed Customer")
                    .build();
            customer.setId(email);
            return Optional.of(customer);
        });
        when(customerProfileRepository.existsById(anyString())).thenReturn(false);

        new UserSeedDataInitializer(
                roleRepository, jdbcTemplate, userRepository, customerProfileRepository).run(null);

        ArgumentCaptor<CustomerProfile> profileCaptor =
                ArgumentCaptor.forClass(CustomerProfile.class);
        verify(customerProfileRepository, times(4)).save(profileCaptor.capture());
        assertThat(profileCaptor.getAllValues())
                .extracting(profile -> profile.getUser().getEmail())
                .containsExactly(
                        "seed.customer@odms.local",
                        "seed.customer.2@odms.local",
                        "seed.customer.3@odms.local",
                        "seed.customer.4@odms.local"
                );
    }
}
