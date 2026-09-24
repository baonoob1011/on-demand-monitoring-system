package com.ondemandmonitoring.config;

import com.ondemandmonitoring.auth.controller.AdminAccountController;
import com.ondemandmonitoring.auth.dto.response.ManagedAccountResponse;
import com.ondemandmonitoring.auth.service.IAdminAccountService;
import com.ondemandmonitoring.role.domain.RoleCode;
import com.ondemandmonitoring.user.service.AuthenticatedUserResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.context.WebApplicationContext;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringJUnitConfig(classes = {
        SecurityConfig.class,
        ActiveAccountFilter.class,
        SecurityAuthorizationTest.TestBeans.class,
        AdminAccountController.class
})
@WebAppConfiguration
class SecurityAuthorizationTest {
    @Autowired
    private WebApplicationContext context;
    @Autowired
    private IAdminAccountService adminAccounts;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
        when(adminAccounts.create(any())).thenReturn(ManagedAccountResponse.builder()
                .email("staff@example.com").role(RoleCode.STAFF)
                .invitationSent(true).passwordChangeRequired(true).build());
    }

    @Test
    void unauthenticatedRequestReturns401() throws Exception {
        mvc.perform(post("/api/admin/accounts")
                        .with(csrf())
                        .contentType("application/json")
                        .content(requestBody()))
                .andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @EnumSource(value = RoleCode.class, names = "ADMIN", mode = EnumSource.Mode.EXCLUDE)
    void everyNonAdminRoleReturns403(RoleCode role) throws Exception {
        mvc.perform(post("/api/admin/accounts")
                        .with(csrf())
                        .with(jwt().authorities(() -> "ROLE_" + role.name()))
                        .contentType("application/json")
                        .content(requestBody()))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanCreateManagedAccount() throws Exception {
        mvc.perform(post("/api/admin/accounts")
                        .with(csrf())
                        .with(jwt().authorities(() -> "ROLE_ADMIN"))
                        .contentType("application/json")
                        .content(requestBody()))
                .andExpect(status().isCreated());
    }

    private String requestBody() {
        return """
                {"email":"staff@example.com","fullName":"Staff","role":"STAFF"}
                """;
    }

    @Configuration
    @EnableWebMvc
    static class TestBeans {
        @Bean
        IAdminAccountService adminAccountService() {
            return mock(IAdminAccountService.class);
        }

        @Bean
        @Qualifier("cognitoAccessTokenDecoder")
        JwtDecoder cognitoAccessTokenDecoder() {
            return mock(JwtDecoder.class);
        }

        @Bean
        AuthenticatedUserResolver authenticatedUserResolver() {
            return mock(AuthenticatedUserResolver.class);
        }
    }
}
