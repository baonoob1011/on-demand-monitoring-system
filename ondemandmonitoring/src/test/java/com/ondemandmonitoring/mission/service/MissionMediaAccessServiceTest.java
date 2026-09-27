package com.ondemandmonitoring.mission.service;

import com.ondemandmonitoring.common.exception.ApiException;
import com.ondemandmonitoring.drone.domain.Drone;
import com.ondemandmonitoring.mission.domain.Mission;
import com.ondemandmonitoring.mission.domain.MissionDroneAssignment;
import com.ondemandmonitoring.mission.domain.MissionOperatorAssignment;
import com.ondemandmonitoring.mission.enums.MissionStatus;
import com.ondemandmonitoring.mission.repository.MissionRepository;
import com.ondemandmonitoring.mission.repository.MissionDroneAssignmentRepository;
import com.ondemandmonitoring.mission.repository.MissionOperatorAssignmentRepository;
import com.ondemandmonitoring.mission.service.impl.MissionMediaAccessServiceImpl;
import com.ondemandmonitoring.order.domain.Order;
import com.ondemandmonitoring.user.domain.User;
import com.ondemandmonitoring.user.service.AuthenticatedUserResolver;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class MissionMediaAccessServiceTest {
    private final MissionRepository missions = mock(MissionRepository.class);
    private final MissionDroneAssignmentRepository drones = mock(MissionDroneAssignmentRepository.class);
    private final MissionOperatorAssignmentRepository operators = mock(MissionOperatorAssignmentRepository.class);
    private final AuthenticatedUserResolver currentUser = mock(AuthenticatedUserResolver.class);
    private final IMissionMediaAccessService service =
            new MissionMediaAccessServiceImpl(missions, drones, operators, currentUser);
    private Mission mission;

    @BeforeEach
    void setup() {
        mission = new Mission();
        mission.setId("mission-id");
        mission.setStatus(MissionStatus.IN_FLIGHT);
        when(missions.findById("mission-id")).thenReturn(Optional.of(mission));
        when(currentUser.getCurrentUserId()).thenReturn("operator-id");
        authenticate(List.of());
    }

    @AfterEach
    void cleanup() { SecurityContextHolder.clearContext(); }

    @Test
    void resolvesMissionCodeToImmutableAuthorizedContext() {
        when(missions.findByMissionCode("MS-001")).thenReturn(Optional.of(mission));
        assignOperator();
        var context = service.authorizeOperator("MS-001");
        assertThat(context.getId()).isEqualTo("mission-id");
        assertThat(context.isCaptureAllowed()).isTrue();
        mission.setStatus(MissionStatus.SCHEDULED);
        assertThat(service.authorizeOperator("mission-id").isCaptureAllowed()).isFalse();
    }

    @Test
    void refusesAnonymousAndUnassignedOperator() {
        assertThatThrownBy(() -> service.authorizeOperator("mission-id")).isInstanceOf(ApiException.class);
        SecurityContextHolder.clearContext();
        assertThatThrownBy(() -> service.authorizeOperator("mission-id")).isInstanceOf(ApiException.class);
    }

    @Test
    void permitsPrivilegedOperatorButStillRequiresAssignedDrone() {
        authenticate(List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        assertThat(service.authorizeOperator("mission-id").isCaptureAllowed()).isTrue();
        assertThatThrownBy(() -> service.requireAssignedDrone("mission-id", "other-drone"))
                .isInstanceOf(ApiException.class).hasMessageContaining("Drone is not assigned");
    }

    @Test
    void completedMissionAllowsOnlyCompletedOperatorAndReleasedMissionDrone() {
        mission.setStatus(MissionStatus.COMPLETED);
        var operator = new MissionOperatorAssignment();
        operator.setOperatorId("operator-id");
        operator.setStatus("COMPLETED");
        when(operators.findByMissionId("mission-id")).thenReturn(List.of(operator));
        var drone = new Drone();
        drone.setId("drone-id");
        var assignment = new MissionDroneAssignment();
        assignment.setDrone(drone);
        assignment.setReleaseReason("MISSION_COMPLETE");
        when(drones.findByMissionId("mission-id")).thenReturn(List.of(assignment));
        assertThatCode(() -> service.requireAssignedDrone("mission-id", "drone-id")).doesNotThrowAnyException();
        assignment.setReleaseReason("REASSIGNED");
        assertThatThrownBy(() -> service.requireAssignedDrone("mission-id", "drone-id"))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void customerCanAccessOnlyOwnedMission() {
        var owner = new User();
        owner.setId("customer-id");
        var order = new Order();
        order.setCustomer(owner);
        mission.setOrder(order);
        when(currentUser.getCurrentUserId()).thenReturn("customer-id");
        when(missions.findByOrder_Customer_Id("customer-id")).thenReturn(List.of(mission));
        assertThat(service.authorizeCustomer("mission-id")).isEqualTo("mission-id");
        assertThat(service.ownCustomerMissionIds()).containsExactly("mission-id");
        when(currentUser.getCurrentUserId()).thenReturn("other-customer");
        assertThatThrownBy(() -> service.authorizeCustomer("mission-id")).isInstanceOf(ApiException.class);
    }

    private void assignOperator() {
        var assignment = new MissionOperatorAssignment();
        assignment.setOperatorId("operator-id");
        when(operators.findByMissionIdAndIsCurrentTrue("mission-id")).thenReturn(Optional.of(assignment));
    }

    private void authenticate(List<SimpleGrantedAuthority> roles) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("operator", "n/a", roles));
    }
}
