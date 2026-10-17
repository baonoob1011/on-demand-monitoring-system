package com.ondemandmonitoring.mission.service;

import com.ondemandmonitoring.common.exception.ApiException;
import com.ondemandmonitoring.common.exception.ErrorCode;
import com.ondemandmonitoring.mission.dto.request.MissionResultRequest;
import com.ondemandmonitoring.mission.enums.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static com.ondemandmonitoring.mission.service.MissionResultSubmissionFixture.Scenario;

class MissionResultSubmitAuthorizationTest {
    final MissionResultSubmissionFixture fixture = new MissionResultSubmissionFixture();

    @ParameterizedTest @EnumSource(Scenario.class)
    void directServiceUsesTheSameCanonicalActorPolicy(Scenario scenario) {
        fixture.prepare(scenario);
        assertEquals(scenario.allowed, fixture.authorization.canSubmitMissionResult(fixture.targetId));
        if (scenario.allowed) {
            var response = fixture.service.upsert(fixture.targetId, new MissionResultRequest());
            assertEquals(MissionResultApprovalStatus.PENDING_MANAGER_APPROVAL, response.getApprovalStatus());
            verify(fixture.results).save(fixture.result);
        } else {
            assertError(ErrorCode.ACCESS_DENIED);
            verify(fixture.results, never()).save(any());
        }
        verifyNoInteractions(fixture.completion);
    }

    @Test void inspectorOwnsTheSingleFinalResultSubmission() {
        fixture.prepare(Scenario.INSPECTOR);
        assertTrue(fixture.authorization.canUploadMissionMedia(fixture.targetId));
        assertTrue(fixture.authorization.canSubmitMissionResult(fixture.targetId));
        fixture.service.upsert(fixture.targetId, new MissionResultRequest());
    }

    @Test void reportingCrewCannotSubmitTheAcceptancePackage() {
        fixture.prepare(Scenario.OPERATOR);
        assertFalse(fixture.authorization.canSubmitMissionResult(fixture.targetId));
        assertError(ErrorCode.ACCESS_DENIED);
    }

    @Test void pilotCannotSubmitEvenWhenOperatorIsUnavailable() {
        fixture.prepare(Scenario.PILOT_WITH_OPERATOR);
        var operator = fixture.crew.stream().filter(a -> a.getAssignedRole() == MissionStaffRole.OPERATOR).findFirst().orElseThrow();
        assertFalse(fixture.authorization.canSubmitMissionResult(fixture.targetId));
        operator.setReleaseReason("REPLACED");
        assertFalse(fixture.authorization.canSubmitMissionResult(fixture.targetId));
        assertError(ErrorCode.ACCESS_DENIED);
    }

    @Test void actorAuthorizationDoesNotBypassOperationalCompletion() {
        fixture.prepare(Scenario.INSPECTOR);
        fixture.mission.setStatus(MissionStatus.PENDING_REVIEW);
        fixture.crew.forEach(a -> { a.setReleasedAt(null); a.setReleaseReason(null); });
        assertTrue(fixture.authorization.canSubmitMissionResult(fixture.targetId));
        assertError(ErrorCode.MISSION_STATUS_INVALID);
        verify(fixture.results, never()).save(any());
    }

    @Test void actorAuthorizationDoesNotBypassChecklistOrEvidenceReadiness() {
        fixture.prepare(Scenario.INSPECTOR);
        doThrow(new ApiException(ErrorCode.CHECKLIST_NOT_READY)).when(fixture.checklist).requireReadyForSubmission(fixture.mission);
        assertError(ErrorCode.CHECKLIST_NOT_READY);
        verify(fixture.results, never()).save(any());
    }

    @ParameterizedTest @EnumSource(value=MissionResultApprovalStatus.class, names={"PENDING_MANAGER_APPROVAL","APPROVED"})
    void submissionPreservesExistingResultLocks(MissionResultApprovalStatus approval) {
        fixture.prepare(Scenario.INSPECTOR); fixture.result.setApprovalStatus(approval);
        assertError(ErrorCode.CHECKLIST_EXECUTION_LOCKED);
        verify(fixture.results, never()).save(any());
    }

    @Test void correctionUpdatesTheSameResultAndReplayStillChecksAuthorization() {
        fixture.prepare(Scenario.INSPECTOR); fixture.result.setApprovalStatus(MissionResultApprovalStatus.REJECTED);
        assertEquals("result-1", fixture.service.upsert(fixture.targetId, new MissionResultRequest()).getId());
        assertError(ErrorCode.CHECKLIST_EXECUTION_LOCKED);
        fixture.actor = fixture.crew.stream().filter(a -> a.getAssignedRole() == MissionStaffRole.OPERATOR).findFirst().orElseThrow().getStaff();
        assertError(ErrorCode.ACCESS_DENIED);
        verify(fixture.results, times(1)).save(any());
    }

    private void assertError(ErrorCode expected) {
        assertEquals(expected, assertThrows(ApiException.class,
                () -> fixture.service.upsert(fixture.targetId, new MissionResultRequest())).getErrorCode());
    }
}
