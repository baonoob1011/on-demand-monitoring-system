package com.ondemandmonitoring.user.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
import org.springframework.http.MediaType;

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

    @Test
    void updateCurrentProfile_returnsUpdatedProfileEnvelope() throws Exception {
        when(userProfileService.updateCurrentProfile(org.mockito.ArgumentMatchers.any()))
                .thenReturn(UserProfileResponse.builder()
                        .id(UUID.randomUUID())
                        .fullName("Updated Name")
                        .email("customer@example.com")
                        .role(RoleCode.CUSTOMER)
                        .customerProfile(CustomerProfileResponse.builder()
                                .companyName("Updated Company")
                                .build())
                        .build());

        mockMvc.perform(patch("/api/v1/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fullName": "Updated Name",
                                  "companyName": "Updated Company"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Profile updated successfully"))
                .andExpect(jsonPath("$.data.fullName").value("Updated Name"))
                .andExpect(jsonPath("$.data.customerProfile.companyName")
                        .value("Updated Company"));
    }

    @Test
    void updateCurrentProfile_rejectsBlankFullName() throws Exception {
        mockMvc.perform(patch("/api/v1/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"   \"}"))
                .andExpect(status().isBadRequest());
    }
}
