package com.ondemandmonitoring.user.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.Mockito.when;

import com.ondemandmonitoring.role.domain.RoleCode;
import com.ondemandmonitoring.user.dto.response.CustomerProfileResponse;
import com.ondemandmonitoring.user.dto.response.UserProfileResponse;
import com.ondemandmonitoring.user.service.UserProfileService;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class UserControllerTest {

    private UserProfileService userProfileService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        userProfileService = org.mockito.Mockito.mock(UserProfileService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new UserController(userProfileService)).build();
    }

    @Test
    void getCurrentProfile_returnsProfileEnvelope() throws Exception {
        UUID userId = UUID.randomUUID();
        when(userProfileService.getCurrentProfile()).thenReturn(UserProfileResponse.builder()
                .id(userId)
                .fullName("Customer Name")
                .email("customer@example.com")
                .role(RoleCode.CUSTOMER)
                .customerProfile(CustomerProfileResponse.builder()
                        .phoneNumber("0901234567")
                        .build())
                .build());

        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(userId.toString()))
                .andExpect(jsonPath("$.data.email").value("customer@example.com"))
                .andExpect(jsonPath("$.data.role").value("CUSTOMER"))
                .andExpect(jsonPath("$.data.customerProfile.phoneNumber").value("0901234567"))
                .andExpect(jsonPath("$.data.emailVerified").doesNotExist())
                .andExpect(jsonPath("$.data.isActive").doesNotExist())
                .andExpect(jsonPath("$.data.linkedProviders").doesNotExist());
    }
}
