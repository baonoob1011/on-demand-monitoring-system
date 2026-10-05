package com.ondemandmonitoring.config;

import com.ondemandmonitoring.common.exception.GlobalExceptionHandler;
import com.ondemandmonitoring.mission.controller.ChecklistEvidenceController;
import com.ondemandmonitoring.mission.service.IChecklistEvidenceService;
import com.ondemandmonitoring.role.domain.Role;
import com.ondemandmonitoring.role.domain.RoleCode;
import com.ondemandmonitoring.user.domain.User;
import com.ondemandmonitoring.user.service.AuthenticatedUserResolver;
import java.util.List;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringJUnitConfig(classes={SecurityConfig.class,ActiveAccountFilter.class,GlobalExceptionHandler.class,
        ChecklistEvidenceController.class,MissionAuthorizationHttpTest.Beans.class,ChecklistEvidenceHttpTest.Beans.class})
@WebAppConfiguration
class ChecklistEvidenceHttpTest {
    @Autowired WebApplicationContext context;
    @Autowired IChecklistEvidenceService evidence;
    @Autowired AuthenticatedUserResolver resolver;
    MockMvc mvc;
    @BeforeEach void prepare() {
        reset(evidence,resolver);
        var user=User.builder().isActive(true).role(Role.builder().code(RoleCode.STAFF).active(true).build()).build();user.setId("actor");
        when(resolver.getCurrentUser()).thenReturn(user);
        mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }
    @Test void unauthenticatedAndCustomerCannotReadWorkingEvidence() throws Exception {
        mvc.perform(get("/api/missions/m/checklist-evidence/candidates")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/missions/m/checklist-evidence/candidates").with(jwt().authorities(()->"ROLE_CUSTOMER"))).andExpect(status().isForbidden());
        verifyNoInteractions(evidence);
    }
    @Test void validSingleAttachUsesBackendIdsAndEnvelope() throws Exception {
        when(evidence.attach(eq("m"),any())).thenReturn(List.of());
        mvc.perform(put("/api/missions/m/checklist-executions/e/evidence/backend-media").with(jwt().authorities(()->"ROLE_STAFF"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"expectedVersion\":4}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true));
        verify(evidence).attach(eq("m"),argThat(request->request.mediaId().equals("backend-media")&&request.targets().getFirst().executionId().equals("e")&&request.targets().getFirst().expectedVersion()==4L));
    }
    @Test void missingVersionAndOversizedBatchRejected() throws Exception {
        mvc.perform(put("/api/missions/m/checklist-executions/e/evidence/a").with(jwt().authorities(()->"ROLE_STAFF"))
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isBadRequest());
        String targets=String.join(",",java.util.Collections.nCopies(101,"{\"executionId\":\"e\",\"expectedVersion\":0}"));
        mvc.perform(post("/api/missions/m/checklist-evidence/batch").with(jwt().authorities(()->"ROLE_STAFF"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"mediaId\":\"a\",\"targets\":["+targets+"]}"))
                .andExpect(status().isBadRequest()); verifyNoInteractions(evidence);
    }
    @Test void candidatePaginationAndDetachVersionAreValidated() throws Exception {
        mvc.perform(get("/api/missions/m/checklist-evidence/candidates?size=101").with(jwt().authorities(()->"ROLE_STAFF"))).andExpect(status().isBadRequest());
        mvc.perform(delete("/api/missions/m/checklist-executions/e/evidence/link?expectedVersion=4").with(jwt().authorities(()->"ROLE_STAFF")))
                .andExpect(status().isOk()); verify(evidence).detach("m","e","link",4,null);
    }
    @Configuration static class Beans {
        @Bean static org.springframework.validation.beanvalidation.MethodValidationPostProcessor methodValidation() {
            var processor = new org.springframework.validation.beanvalidation.MethodValidationPostProcessor();
            processor.setProxyTargetClass(true); return processor;
        }
        @Bean IChecklistEvidenceService evidence() {return mock(IChecklistEvidenceService.class);}
    }
}
