package com.ondemandmonitoring.mission.service;

import com.ondemandmonitoring.mission.domain.*;
import com.ondemandmonitoring.mission.enums.*;
import com.ondemandmonitoring.mission.repository.*;
import com.ondemandmonitoring.mission.service.impl.MissionAuthorizationServiceImpl;
import com.ondemandmonitoring.role.domain.*;
import com.ondemandmonitoring.user.domain.User;
import com.ondemandmonitoring.user.service.AuthenticatedUserResolver;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MonitoringChecklistAuthorizationTest {
    MissionRepository missions = mock(MissionRepository.class);
    MissionStaffAssignmentRepository assignments = mock(MissionStaffAssignmentRepository.class);
    AuthenticatedUserResolver resolver = mock(AuthenticatedUserResolver.class);
    IMissionAuthorizationService policy = new MissionAuthorizationServiceImpl(assignments, missions, resolver);
    Mission mission;
    User actor;

    @BeforeEach void prepare() {
        mission = new Mission(); mission.setId("m"); mission.setStatus(MissionStatus.IN_FLIGHT);
        actor = user("actor");
        when(resolver.getCurrentUser()).thenReturn(actor);
        when(missions.findById("m")).thenReturn(Optional.of(mission));
    }

    User user(String id) {
        var user = User.builder().isActive(true).role(Role.builder().code(RoleCode.STAFF).active(true).build()).build();
        user.setId(id); return user;
    }

    MissionStaffAssignment crew(User user, MissionStaffRole role) {
        return MissionStaffAssignment.builder().staff(user).assignedRole(role).isCurrent(true)
                .responseStatus(StaffResponseStatus.ACCEPTED).build();
    }

    @ParameterizedTest @EnumSource(MissionStaffRole.class)
    void onlyOperatorOrPilotFallbackExecutes(MissionStaffRole role) {
        when(assignments.findByMissionId("m")).thenReturn(List.of(crew(actor, role)));
        assertEquals(role == MissionStaffRole.OPERATOR || role == MissionStaffRole.PILOT,
                policy.canExecuteMonitoringChecklist("m"));
        assertFalse(policy.canExecuteMonitoringChecklist("other"));
    }

    @ParameterizedTest @EnumSource(StaffResponseStatus.class)
    void operatorMustAccept(StaffResponseStatus response) {
        var operator = crew(actor, MissionStaffRole.OPERATOR); operator.setResponseStatus(response);
        when(assignments.findByMissionId("m")).thenReturn(List.of(operator));
        assertEquals(response == StaffResponseStatus.ACCEPTED, policy.canExecuteMonitoringChecklist("m"));
    }

    @Test void pilotFallbackRequiresNoUsableOperator() {
        var pilot = crew(actor, MissionStaffRole.PILOT);
        var operator = crew(user("operator"), MissionStaffRole.OPERATOR);
        when(assignments.findByMissionId("m")).thenReturn(List.of(pilot, operator));
        assertFalse(policy.canExecuteMonitoringChecklist("m"));
        operator.setResponseStatus(StaffResponseStatus.PENDING); assertTrue(policy.canExecuteMonitoringChecklist("m"));
        operator.setResponseStatus(StaffResponseStatus.REJECTED); assertTrue(policy.canExecuteMonitoringChecklist("m"));
        operator.setResponseStatus(StaffResponseStatus.ACCEPTED);
        operator.getStaff().setIsActive(false); assertTrue(policy.canExecuteMonitoringChecklist("m"));
        operator.getStaff().setIsActive(true);
        operator.setReleasedAt(Instant.now()); assertTrue(policy.canExecuteMonitoringChecklist("m"));
        operator.setReleasedAt(null); operator.setIsCurrent(false); assertTrue(policy.canExecuteMonitoringChecklist("m"));
    }

    @Test void inactiveReleasedNonCurrentAndUnassignedActorsDenied() {
        var operator = crew(actor, MissionStaffRole.OPERATOR);
        when(assignments.findByMissionId("m")).thenReturn(List.of(operator));
        actor.setIsActive(false); assertFalse(policy.canExecuteMonitoringChecklist("m"));
        actor.setIsActive(true); actor.getRole().setActive(false); assertFalse(policy.canExecuteMonitoringChecklist("m"));
        actor.getRole().setActive(true); operator.setIsCurrent(false); assertFalse(policy.canExecuteMonitoringChecklist("m"));
        operator.setIsCurrent(true); operator.setReleasedAt(Instant.now()); assertFalse(policy.canExecuteMonitoringChecklist("m"));
        when(assignments.findByMissionId("m")).thenReturn(List.of()); assertFalse(policy.canExecuteMonitoringChecklist("m"));
    }

    @ParameterizedTest @EnumSource(value = RoleCode.class, names = "STAFF", mode = EnumSource.Mode.EXCLUDE)
    void systemRolesCannotBecomeExecutionActors(RoleCode role) {
        actor.getRole().setCode(role);
        when(assignments.findByMissionId("m")).thenReturn(List.of(crew(actor, MissionStaffRole.OPERATOR)));
        assertFalse(policy.canExecuteMonitoringChecklist("m"));
    }

    @ParameterizedTest @EnumSource(MissionStatus.class)
    void mutationWindowIsExplicit(MissionStatus status) {
        mission.setStatus(status);
        when(assignments.findByMissionId("m")).thenReturn(List.of(crew(actor, MissionStaffRole.OPERATOR)));
        assertEquals(Set.of(MissionStatus.IN_FLIGHT, MissionStatus.IN_PROGRESS, MissionStatus.RETURNING,
                MissionStatus.POSTFLIGHT_CHECKING, MissionStatus.PENDING_REVIEW).contains(status), policy.canExecuteMonitoringChecklist("m"));
    }

    @Test void successfulFinalCrewCanFinishAfterOperationalRelease() {
        mission.setStatus(MissionStatus.COMPLETED);
        var operator = crew(actor, MissionStaffRole.OPERATOR);
        when(assignments.findByMissionId("m")).thenReturn(List.of(operator));
        assertFalse(policy.canExecuteMonitoringChecklist("m"));
        operator.setIsCurrent(false); operator.setReleasedAt(Instant.now()); operator.setReleaseReason("MISSION_COMPLETE");
        assertTrue(policy.canExecuteMonitoringChecklist("m"));
        operator.setIsCurrent(true); assertTrue(policy.canExecuteMonitoringChecklist("m"));
        operator.setReleaseReason("REPLACED"); assertFalse(policy.canExecuteMonitoringChecklist("m"));
    }

    @Test void pendingReviewKeepsOperatorPrimaryAndPilotFallbackButDeniesInspector() {
        mission.setStatus(MissionStatus.PENDING_REVIEW);
        var pilot = crew(actor, MissionStaffRole.PILOT);
        var operator = crew(user("operator"), MissionStaffRole.OPERATOR);
        when(assignments.findByMissionId("m")).thenReturn(List.of(pilot, operator));
        assertFalse(policy.canExecuteMonitoringChecklist("m"));
        operator.setReleasedAt(Instant.now());
        assertTrue(policy.canExecuteMonitoringChecklist("m"));
        when(assignments.findByMissionId("m")).thenReturn(List.of(crew(actor, MissionStaffRole.OPERATOR)));
        assertTrue(policy.canExecuteMonitoringChecklist("m"));
        when(assignments.findByMissionId("m")).thenReturn(List.of(crew(actor, MissionStaffRole.INSPECTOR)));
        assertFalse(policy.canExecuteMonitoringChecklist("m"));
        assertFalse(policy.canSubmitMissionResult("m"));
    }
}
