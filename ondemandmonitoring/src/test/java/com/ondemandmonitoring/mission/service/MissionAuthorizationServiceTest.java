package com.ondemandmonitoring.mission.service;

import com.ondemandmonitoring.mission.domain.Mission;
import com.ondemandmonitoring.mission.domain.MissionStaffAssignment;
import com.ondemandmonitoring.mission.enums.MissionStaffRole;
import com.ondemandmonitoring.mission.enums.MissionStatus;
import com.ondemandmonitoring.mission.enums.StaffResponseStatus;
import com.ondemandmonitoring.mission.repository.MissionRepository;
import com.ondemandmonitoring.mission.repository.MissionStaffAssignmentRepository;
import com.ondemandmonitoring.mission.service.impl.MissionAuthorizationServiceImpl;
import com.ondemandmonitoring.role.domain.Role;
import com.ondemandmonitoring.role.domain.RoleCode;
import com.ondemandmonitoring.user.domain.User;
import com.ondemandmonitoring.user.service.AuthenticatedUserResolver;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class MissionAuthorizationServiceTest {
    private final MissionStaffAssignmentRepository assignments = mock(MissionStaffAssignmentRepository.class);
    private final MissionRepository missions = mock(MissionRepository.class);
    private final AuthenticatedUserResolver resolver = mock(AuthenticatedUserResolver.class);
    private final IMissionAuthorizationService policy = new MissionAuthorizationServiceImpl(assignments, missions, resolver);
    private User staff;
    private Mission mission;

    @BeforeEach
    void setup() {
        staff = User.builder().role(Role.builder().code(RoleCode.STAFF).active(true).build()).isActive(true).build();
        staff.setId("staff-1");
        mission = new Mission();
        mission.setId("mission-1");
        mission.setStatus(MissionStatus.SCHEDULED);
        when(resolver.getCurrentUser()).thenReturn(staff);
        when(resolver.getCurrentUserId()).thenReturn(staff.getId());
        when(missions.findById(mission.getId())).thenReturn(Optional.of(mission));
    }

    @ParameterizedTest
    @EnumSource(MissionStaffRole.class)
    void flightControlRequiresAcceptedPilotAssignment(MissionStaffRole role) {
        assign(role, StaffResponseStatus.ACCEPTED);
        assertThat(policy.canControlFlight("mission-1")).isEqualTo(role == MissionStaffRole.PILOT);
        assertThat(policy.canControlFlight("another-mission")).isFalse();
    }

    @Test
    void pendingCrewCanRespondButCannotExecuteOrRespondOnBehalfOfAnotherUser() {
        mission.setStatus(MissionStatus.WAITING_CREW_CONFIRMATION);
        assign(MissionStaffRole.PILOT, StaffResponseStatus.PENDING);
        assertThat(policy.canRespondToMission("mission-1")).isTrue();
        assertThat(policy.canRespondToMission("mission-1", "staff-2")).isFalse();
        assertThat(policy.canControlFlight("mission-1")).isFalse();
        mission.setStatus(MissionStatus.IN_FLIGHT);
        assertThat(policy.canRespondToMission("mission-1")).isFalse();
    }

    @Test
    void pilotCoversOptionalTasksOnlyUntilASpecialistIsAssigned() {
        var pilot = assign(MissionStaffRole.PILOT, StaffResponseStatus.ACCEPTED);
        assertThat(policy.canInspectDevice("mission-1")).isTrue();
        assertThat(policy.canOperatePayload("mission-1")).isTrue();
        var inspector = MissionStaffAssignment.builder().staff(otherStaff()).assignedRole(MissionStaffRole.INSPECTOR)
                .responseStatus(StaffResponseStatus.PENDING).isCurrent(true).build();
        var operator = MissionStaffAssignment.builder().staff(otherStaff()).assignedRole(MissionStaffRole.OPERATOR)
                .responseStatus(StaffResponseStatus.ACCEPTED).isCurrent(true).build();
        when(assignments.findAllByMissionIdAndIsCurrentTrue("mission-1")).thenReturn(List.of(pilot, inspector, operator));
        assertThat(policy.canInspectDevice("mission-1")).isFalse();
        assertThat(policy.canOperatePayload("mission-1")).isFalse();
        assertThat(policy.canControlFlight("mission-1")).isTrue();
    }

    @ParameterizedTest
    @EnumSource(value = RoleCode.class, names = "STAFF", mode = EnumSource.Mode.EXCLUDE)
    void systemRoleDoesNotBypassCrewPermissions(RoleCode role) {
        assign(MissionStaffRole.PILOT, StaffResponseStatus.ACCEPTED);
        staff.getRole().setCode(role);
        assertThat(policy.canControlFlight("mission-1")).isFalse();
        assertThat(policy.canMaintainDevice("mission-1")).isFalse();
        assertThat(policy.canManageMissions()).isEqualTo(role == RoleCode.ADMIN || role == RoleCode.MANAGER);
    }

    @Test
    void inactiveReleasedRejectedAndTerminalAssignmentsCannotMutateMission() {
        var assignment = assign(MissionStaffRole.PILOT, StaffResponseStatus.ACCEPTED);
        assignment.setReleasedAt(Instant.now());
        assertThat(policy.canControlFlight("mission-1")).isFalse();
        assignment.setReleasedAt(null);
        assignment.setIsCurrent(false);
        assertThat(policy.canControlFlight("mission-1")).isFalse();
        assignment.setIsCurrent(true);
        assignment.setResponseStatus(StaffResponseStatus.REJECTED);
        assertThat(policy.canControlFlight("mission-1")).isFalse();
        assignment.setResponseStatus(StaffResponseStatus.ACCEPTED);
        staff.setIsActive(false);
        assertThat(policy.canControlFlight("mission-1")).isFalse();
        staff.setIsActive(true);
        mission.setStatus(MissionStatus.COMPLETED);
        assertThat(policy.canControlFlight("mission-1")).isFalse();
    }

    @Test
    void historicalCrewCanReadButCannotControlAndOnlyFinalCaptureCrewCanFinishUploads() {
        mission.setStatus(MissionStatus.COMPLETED);
        var entry = MissionStaffAssignment.builder().staff(staff).assignedRole(MissionStaffRole.OPERATOR)
                .responseStatus(StaffResponseStatus.ACCEPTED).isCurrent(false).releaseReason("MISSION_COMPLETE").build();
        when(assignments.existsByMissionIdAndStaffId("mission-1", "staff-1")).thenReturn(true);
        when(assignments.findByMissionId("mission-1")).thenReturn(List.of(entry));
        assertThat(policy.canViewMission("mission-1")).isTrue();
        assertThat(policy.canUploadMissionMedia("mission-1")).isTrue();
        assertThat(policy.canControlFlight("mission-1")).isFalse();
        entry.setReleaseReason("REPLACED");
        assertThat(policy.canUploadMissionMedia("mission-1")).isFalse();
    }

    @Test
    void staffCannotQueryAnotherStaffsMissions() {
        assertThat(policy.canViewStaffMissions("staff-1")).isTrue();
        assertThat(policy.canViewStaffMissions("staff-2")).isFalse();
        assertThat(policy.canViewMission("mission-1")).isFalse();
    }

    private MissionStaffAssignment assign(MissionStaffRole role, StaffResponseStatus response) {
        var entry = MissionStaffAssignment.builder().staff(staff).mission(mission).assignedRole(role)
                .responseStatus(response).isCurrent(true).build();
        when(assignments.findAllByMissionIdAndIsCurrentTrue("mission-1")).thenReturn(List.of(entry));
        when(assignments.findAllByMissionIdAndStaffIdAndIsCurrentTrueOrderByAssignedAtDesc("mission-1", "staff-1"))
                .thenReturn(List.of(entry));
        return entry;
    }

    private User otherStaff() {
        User other = new User();
        other.setId("staff-2");
        return other;
    }
}
