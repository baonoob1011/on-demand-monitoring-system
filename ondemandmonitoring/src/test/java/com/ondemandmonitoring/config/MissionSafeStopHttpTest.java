package com.ondemandmonitoring.config;

import com.ondemandmonitoring.common.exception.GlobalExceptionHandler;
import com.ondemandmonitoring.mission.controller.MissionSafeStopController;
import com.ondemandmonitoring.mission.service.IMissionSafeStopService;
import com.ondemandmonitoring.role.domain.*;
import com.ondemandmonitoring.role.domain.Role;
import com.ondemandmonitoring.user.domain.User;
import com.ondemandmonitoring.user.service.AuthenticatedUserResolver;
import java.util.List;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringJUnitConfig(classes={SecurityConfig.class,ActiveAccountFilter.class,GlobalExceptionHandler.class,
        MissionSafeStopController.class,MissionAuthorizationHttpTest.Beans.class,MissionSafeStopHttpTest.Beans.class})
@WebAppConfiguration
class MissionSafeStopHttpTest {
    @Autowired WebApplicationContext context;
    @Autowired AuthenticatedUserResolver actor;
    @Autowired IMissionSafeStopService service;
    MockMvc mvc;
    @BeforeEach void prepare() {
        reset(actor,service); when(actor.getCurrentUser()).thenReturn(User.builder().isActive(true)
                .role(Role.builder().code(RoleCode.MANAGER).active(true).build()).build());
        mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }
    @Test void planningAuthorityCannotConfirmActualLanding() throws Exception {
        String body="{\"landedAndDisarmed\":true,\"controlSessionClosed\":true,\"confirmation\":\"Landed\"}";
        for(String role:List.of("MANAGER","ADMIN","CUSTOMER"))
            mvc.perform(post("/api/missions/m/safe-stop/confirm").with(jwt().authorities(() -> "ROLE_"+role))
                    .contentType("application/json").content(body)).andExpect(status().isForbidden());
        mvc.perform(post("/api/missions/m/safe-stop/confirm").with(jwt().authorities(() -> "ROLE_STAFF"))
                .contentType("application/json").content(body)).andExpect(status().isOk());
        verify(service).confirm(eq("m"),any());
    }
    @Test void requestingAndRecordingCleanupRequireManagerAdmin() throws Exception {
        for(String path:List.of("/api/missions/m/safe-stop","/api/missions/m/safe-stop/cleanup","/api/missions/m/safe-stop/fail")) {
            String body="{\"expectedPlanRevision\":4,\"reason\":\"Weather\"}";
            mvc.perform(post(path).contentType("application/json").content(body)).andExpect(status().isUnauthorized());
            for(String role:List.of("STAFF","CUSTOMER"))
                mvc.perform(post(path).with(jwt().authorities(() -> "ROLE_"+role)).contentType("application/json").content(body)).andExpect(status().isForbidden());
            for(String role:List.of("MANAGER","ADMIN"))
                mvc.perform(post(path).with(jwt().authorities(() -> "ROLE_"+role)).contentType("application/json").content(body)).andExpect(status().isOk());
        }
    }
    @Test void unconfirmedOperationalAttestationIsRejected() throws Exception {
        mvc.perform(post("/api/missions/m/safe-stop/confirm").with(jwt().authorities(() -> "ROLE_STAFF"))
                .contentType("application/json").content("{\"landedAndDisarmed\":false,\"controlSessionClosed\":true,\"confirmation\":\"Unknown\"}"))
                .andExpect(status().isBadRequest()); verifyNoInteractions(service);
    }
    @Configuration static class Beans { @Bean IMissionSafeStopService safeStop(){return mock(IMissionSafeStopService.class);} }
}
