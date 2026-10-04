package com.ondemandmonitoring.config;

import com.ondemandmonitoring.media.mapper.MediaAssetMapper;
import com.ondemandmonitoring.mission.controller.MissionController;
import com.ondemandmonitoring.mission.controller.MissionAccessController;
import com.ondemandmonitoring.mission.domain.Mission;
import com.ondemandmonitoring.mission.domain.MissionStaffAssignment;
import com.ondemandmonitoring.mission.enums.MissionStaffRole;
import com.ondemandmonitoring.mission.enums.MissionStatus;
import com.ondemandmonitoring.mission.enums.StaffResponseStatus;
import com.ondemandmonitoring.mission.repository.MissionRepository;
import com.ondemandmonitoring.mission.repository.MissionStaffAssignmentRepository;
import com.ondemandmonitoring.mission.service.IMissionAuthorizationService;
import com.ondemandmonitoring.mission.service.IMissionMediaUploadService;
import com.ondemandmonitoring.mission.service.IMissionService;
import com.ondemandmonitoring.mission.service.impl.MissionAuthorizationServiceImpl;
import com.ondemandmonitoring.role.domain.Role;
import com.ondemandmonitoring.role.domain.RoleCode;
import com.ondemandmonitoring.user.domain.User;
import com.ondemandmonitoring.user.service.AuthenticatedUserResolver;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@SpringJUnitConfig(classes = {SecurityConfig.class, ActiveAccountFilter.class,
        MissionController.class, MissionAccessController.class, MissionAuthorizationHttpTest.Beans.class})
@WebAppConfiguration
class MissionAuthorizationHttpTest {
    @Autowired WebApplicationContext context;
    @Autowired AuthenticatedUserResolver resolver;
    @Autowired MissionRepository missions;
    @Autowired MissionStaffAssignmentRepository assignments;
    @Autowired IMissionService service;
    private MockMvc mvc;
    private User user;
    private Mission mission;

    @BeforeEach
    void setup() {
        reset(resolver, missions, assignments, service);
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        user = User.builder().role(Role.builder().code(RoleCode.STAFF).active(true).build())
                .isActive(true).build();
        user.setId("staff-1");
        mission = new Mission();
        mission.setId("mission-1");
        mission.setStatus(MissionStatus.SCHEDULED);
        when(resolver.getCurrentUser()).thenReturn(user);
        when(resolver.getCurrentUserId()).thenReturn(user.getId());
        when(missions.findById("mission-1")).thenReturn(Optional.of(mission));
    }

    @Test
    void acceptedOperatorCanConnectButInspectorAndPilotCannot() throws Exception {
        crew(MissionStaffRole.INSPECTOR, StaffResponseStatus.ACCEPTED);
        mvc.perform(post("/api/missions/mission-1/connect").with(jwt().authorities(() -> "ROLE_STAFF")).with(csrf()))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
        crew(MissionStaffRole.PILOT, StaffResponseStatus.ACCEPTED);
        mvc.perform(post("/api/missions/mission-1/connect").with(jwt().authorities(() -> "ROLE_STAFF")).with(csrf()))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
        crew(MissionStaffRole.OPERATOR, StaffResponseStatus.ACCEPTED);
        mvc.perform(post("/api/missions/mission-1/connect").with(jwt().authorities(() -> "ROLE_STAFF")).with(csrf()))
                .andExpect(status().isOk());
        verify(service).connectGcs("mission-1");
    }

    @Test
    void managerCoordinatesButCannotFly() throws Exception {
        user.getRole().setCode(RoleCode.MANAGER);
        crew(MissionStaffRole.PILOT, StaffResponseStatus.ACCEPTED);
        mvc.perform(get("/api/missions/pending-assignment")
                        .with(jwt().authorities(() -> "ROLE_MANAGER"))).andExpect(status().isOk());
        mvc.perform(post("/api/missions/mission-1/connect").with(jwt().authorities(() -> "ROLE_MANAGER")).with(csrf()))
                .andExpect(status().isForbidden());
        verify(service, never()).connectGcs(anyString());
    }

    @Test
    void pendingStaffCannotAcceptForAnotherStaff() throws Exception {
        mission.setStatus(MissionStatus.WAITING_CREW_CONFIRMATION);
        crew(MissionStaffRole.PILOT, StaffResponseStatus.PENDING);
        mvc.perform(patch("/api/missions/mission-1/accept-staff").param("staffId", "staff-2")
                        .with(jwt().authorities(() -> "ROLE_STAFF")).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(patch("/api/missions/mission-1/accept-staff").param("staffId", "staff-1")
                        .with(jwt().authorities(() -> "ROLE_STAFF")).with(csrf())).andExpect(status().isOk());
        verify(service).acceptMission("mission-1", "staff-1");
    }

    @Test
    void unassignedStaffAndCustomerCannotReadMission() throws Exception {
        mvc.perform(get("/api/missions/mission-1").with(jwt().authorities(() -> "ROLE_STAFF"))).andExpect(status().isForbidden());
        user.getRole().setCode(RoleCode.CUSTOMER);
        crew(MissionStaffRole.PILOT, StaffResponseStatus.ACCEPTED);
        mvc.perform(get("/api/missions/mission-1").with(jwt().authorities(() -> "ROLE_CUSTOMER"))).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    void permissionsReflectAcceptedMissionRoleWithoutPilotFallback() throws Exception {
        crew(MissionStaffRole.PILOT, StaffResponseStatus.ACCEPTED);
        mvc.perform(get("/api/missions/mission-1/permissions")
                        .with(jwt().authorities(() -> "ROLE_STAFF")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.canControlFlight").value(true))
                .andExpect(jsonPath("$.data.canInspectDevice").value(false))
                .andExpect(jsonPath("$.data.canOperatePayload").value(false))
                .andExpect(jsonPath("$.data.canUploadMedia").value(true))
                .andExpect(jsonPath("$.data.canMaintainDevice").value(false));
        crew(MissionStaffRole.OPERATOR, StaffResponseStatus.ACCEPTED);
        mvc.perform(get("/api/missions/mission-1/permissions")
                        .with(jwt().authorities(() -> "ROLE_STAFF")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.canControlFlight").value(false))
                .andExpect(jsonPath("$.data.canOperatePayload").value(true))
                .andExpect(jsonPath("$.data.canInspectDevice").value(false))
                .andExpect(jsonPath("$.data.canUploadMedia").value(true));
        crew(MissionStaffRole.INSPECTOR, StaffResponseStatus.ACCEPTED);
        mvc.perform(get("/api/missions/mission-1/permissions")
                        .with(jwt().authorities(() -> "ROLE_STAFF")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.canControlFlight").value(false))
                .andExpect(jsonPath("$.data.canInspectDevice").value(true))
                .andExpect(jsonPath("$.data.canUploadMedia").value(false));
    }

    @Test
    void permissionsRejectUnassignedStaffAndDoNotGiveManagerFlightControl() throws Exception {
        mvc.perform(get("/api/missions/mission-1/permissions")
                        .with(jwt().authorities(() -> "ROLE_STAFF")))
                .andExpect(status().isForbidden());
        user.getRole().setCode(RoleCode.MANAGER);
        mvc.perform(get("/api/missions/mission-1/permissions")
                        .with(jwt().authorities(() -> "ROLE_MANAGER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.canControlFlight").value(false));
    }

    private void crew(MissionStaffRole role, StaffResponseStatus response) {
        var crew = List.of(MissionStaffAssignment.builder().staff(user).mission(mission)
                .assignedRole(role).responseStatus(response).isCurrent(true).build());
        when(assignments.findAllByMissionIdAndIsCurrentTrue("mission-1")).thenReturn(crew);
        when(assignments.findAllByMissionIdAndStaffIdAndIsCurrentTrueOrderByAssignedAtDesc("mission-1", "staff-1"))
                .thenReturn(crew);
    }

    @Configuration
    @EnableWebMvc
    static class Beans {
        @Bean IMissionService missionService() { return mock(IMissionService.class); }
        @Bean IMissionMediaUploadService missionMediaUploadService() { return mock(IMissionMediaUploadService.class); }
        @Bean MediaAssetMapper mediaAssetMapper() { return mock(MediaAssetMapper.class); }
        @Bean AuthenticatedUserResolver resolver() { return mock(AuthenticatedUserResolver.class); }
        @Bean MissionRepository missions() { return mock(MissionRepository.class); }
        @Bean MissionStaffAssignmentRepository assignments() { return mock(MissionStaffAssignmentRepository.class); }
        @Bean JwtDecoder cognitoAccessTokenDecoder() { return mock(JwtDecoder.class); }
        @Bean("missionAuthorizationService")
        IMissionAuthorizationService policy(MissionRepository missions,
                MissionStaffAssignmentRepository assignments, AuthenticatedUserResolver resolver) {
            return new MissionAuthorizationServiceImpl(assignments, missions, resolver);
        }
    }
}
