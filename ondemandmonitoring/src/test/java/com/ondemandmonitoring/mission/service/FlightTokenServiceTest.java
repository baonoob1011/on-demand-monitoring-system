package com.ondemandmonitoring.mission.service;

import com.ondemandmonitoring.common.exception.ApiException;
import com.ondemandmonitoring.mission.domain.FlightToken;
import com.ondemandmonitoring.mission.domain.MissionOperatorAssignment;
import com.ondemandmonitoring.mission.repository.FlightTokenRepository;
import com.ondemandmonitoring.mission.repository.MissionOperatorAssignmentRepository;
import com.ondemandmonitoring.mission.service.impl.FlightTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FlightTokenServiceTest {

    @Mock
    private FlightTokenRepository flightTokenRepository;

    @Mock
    private MissionOperatorAssignmentRepository missionOperatorAssignmentRepository;

    private FlightTokenService flightTokenService;

    @BeforeEach
    void setUp() {
        flightTokenService = new FlightTokenService(flightTokenRepository, missionOperatorAssignmentRepository);
    }

    @Test
    @DisplayName("issueFlightToken generates valid 60-minute token for assigned operator")
    void issueFlightToken_Success() {
        String missionId = "M-101";
        String droneCode = "DRONE-01";
        String operatorId = "OP-1";

        MissionOperatorAssignment assignment = new MissionOperatorAssignment();
        assignment.setOperatorId(operatorId);
        assignment.setIsCurrent(true);

        when(missionOperatorAssignmentRepository.findByMissionIdAndIsCurrentTrue(missionId))
                .thenReturn(Optional.of(assignment));

        when(flightTokenRepository.save(any(FlightToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

        FlightToken token = flightTokenService.issueFlightToken(missionId, droneCode, operatorId);

        assertThat(token).isNotNull();
        assertThat(token.getMissionId()).isEqualTo(missionId);
        assertThat(token.getDroneCode()).isEqualTo(droneCode);
        assertThat(token.getDeviceCode()).isEqualTo(droneCode);
        assertThat(token.getOperatorId()).isEqualTo(operatorId);
        assertThat(token.getExpiresAt()).isAfter(token.getIssuedAt());
        assertThat(token.getExpiresAt().getEpochSecond() - token.getIssuedAt().getEpochSecond())
                .isEqualTo(FlightTokenService.TOKEN_TTL_SECONDS); // 3600 seconds = 60 minutes
    }

    @Test
    @DisplayName("issueFlightToken throws ApiException when requested operator is not assigned to mission")
    void issueFlightToken_UnassignedOperator_ThrowsException() {
        String missionId = "M-101";
        String droneCode = "DRONE-01";
        String requestedOperatorId = "OP-WRONG";

        MissionOperatorAssignment assignment = new MissionOperatorAssignment();
        assignment.setOperatorId("OP-CORRECT");
        assignment.setIsCurrent(true);

        when(missionOperatorAssignmentRepository.findByMissionIdAndIsCurrentTrue(missionId))
                .thenReturn(Optional.of(assignment));

        assertThatThrownBy(() -> flightTokenService.issueFlightToken(missionId, droneCode, requestedOperatorId))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("not assigned to mission");
    }

    @Test
    @DisplayName("validateToken returns true for active unexpired token")
    void validateToken_ValidToken_ReturnsTrue() {
        String tokenValue = "valid-token-xyz";
        FlightToken token = new FlightToken();
        token.setTokenValue(tokenValue);
        token.setIssuedAt(Instant.now());
        token.setExpiresAt(Instant.now().plusSeconds(3600));
        token.setUsed(false);
        token.setRevoked(false);

        when(flightTokenRepository.findByTokenValue(tokenValue)).thenReturn(Optional.of(token));

        boolean isValid = flightTokenService.validateToken(tokenValue);
        assertThat(isValid).isTrue();
    }
}
