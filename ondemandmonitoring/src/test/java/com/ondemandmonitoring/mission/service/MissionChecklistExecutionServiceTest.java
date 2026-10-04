package com.ondemandmonitoring.mission.service;

import com.ondemandmonitoring.common.exception.*;
import com.ondemandmonitoring.mission.domain.*;
import com.ondemandmonitoring.mission.enums.*;
import com.ondemandmonitoring.mission.dto.request.ChecklistExecutionUpdateRequest;
import com.ondemandmonitoring.mission.mapper.MissionChecklistExecutionMapper;
import com.ondemandmonitoring.mission.repository.*;
import com.ondemandmonitoring.mission.service.impl.MissionChecklistExecutionServiceImpl;
import com.ondemandmonitoring.order.domain.*;
import com.ondemandmonitoring.order.domain.Order;
import com.ondemandmonitoring.order.repository.OrderChecklistItemRepository;
import com.ondemandmonitoring.user.service.AuthenticatedUserResolver;
import java.util.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MissionChecklistExecutionServiceTest {
    MissionRepository missions = mock(MissionRepository.class);
    OrderChecklistItemRepository items = mock(OrderChecklistItemRepository.class);
    MissionChecklistExecutionRepository executions = mock(MissionChecklistExecutionRepository.class);
    MissionResultRepository results = mock(MissionResultRepository.class);
    IMissionAuthorizationService auth = mock(IMissionAuthorizationService.class);
    AuthenticatedUserResolver resolver = mock(AuthenticatedUserResolver.class);
    MissionChecklistExecutionServiceImpl service = new MissionChecklistExecutionServiceImpl(missions, items,
            executions, results, auth, resolver, org.mapstruct.factory.Mappers.getMapper(MissionChecklistExecutionMapper.class));
    Mission mission; Order order; OrderChecklistItem item; MissionChecklistExecution execution;

    @BeforeEach void prepare() {
        order = new Order(); order.setId("o");
        mission = new Mission(); mission.setId("m"); mission.setOrder(order); mission.setStatus(MissionStatus.IN_FLIGHT);
        item = new OrderChecklistItem(); item.setId("i"); item.setOrder(order); item.setContent("Historical");
        execution = new MissionChecklistExecution(); execution.setId("e"); execution.setVersion(0L);
        execution.setMission(mission); execution.setMissionId("m"); execution.setOrderId("o");
        execution.setOrderChecklistItem(item); execution.setOrderChecklistItemId("i");
        when(missions.findByIdForUpdate("m")).thenReturn(Optional.of(mission));
        when(missions.findById("m")).thenReturn(Optional.of(mission));
        when(executions.findById("e")).thenReturn(Optional.of(execution));
        when(executions.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
        when(auth.canExecuteMonitoringChecklist("m")).thenReturn(true);
        when(auth.canViewMission("m")).thenReturn(true);
        when(resolver.getCurrentUserId()).thenReturn("authenticated-actor");
        when(items.findAllByOrderIdInOrderByDisplayOrderAscIdAsc(List.of("o"))).thenReturn(List.of(item));
        when(executions.findOrderedByMissionId("m")).thenReturn(List.of(execution));
    }

    ChecklistExecutionUpdateRequest request(ChecklistExecutionStatus status) {
        var request = new ChecklistExecutionUpdateRequest(); request.setExpectedVersion(0L);
        request.setExecutionStatus(status); request.setAssessmentStatus(ChecklistAssessmentStatus.NOT_ASSESSED); return request;
    }
    void error(ErrorCode code, Runnable call) { assertEquals(code, assertThrows(ApiException.class, call::run).getErrorCode()); }

    @Test void progressesCompletesWithoutChangingSnapshotOrMissionAndClearsReason() {
        service.update("m", "e", request(ChecklistExecutionStatus.IN_PROGRESS));
        assertNotNull(execution.getStartedAt()); assertNull(execution.getCompletedAt());
        var request = request(ChecklistExecutionStatus.COMPLETED); request.setAssessmentStatus(ChecklistAssessmentStatus.NON_COMPLIANT);
        request.setObservation("  Fence damaged  "); request.setUnableToVerifyReason("obsolete");
        var response = service.update("m", "e", request);
        assertEquals("Fence damaged", response.getObservation()); assertNull(response.getUnableToVerifyReason());
        assertEquals("authenticated-actor", response.getLastModifiedBy()); assertNotNull(response.getCompletedAt());
        assertEquals(MissionStatus.IN_FLIGHT, mission.getStatus()); assertEquals("Historical", item.getContent());
        verify(items, never()).save(any()); verify(missions, never()).save(any());
    }

    @Test void atomicCompletionWithNoAssessmentIsAllowed() {
        var response = service.update("m", "e", request(ChecklistExecutionStatus.COMPLETED));
        assertEquals(ChecklistAssessmentStatus.NOT_ASSESSED, response.getAssessmentStatus());
        assertNotNull(response.getStartedAt()); assertNotNull(response.getCompletedAt());
    }

    @Test void unableRequiresReasonAndTrimsIt() {
        var request = request(ChecklistExecutionStatus.UNABLE_TO_VERIFY);
        error(ErrorCode.INVALID_REQUEST, () -> service.update("m", "e", request));
        request.setUnableToVerifyReason("   "); error(ErrorCode.INVALID_REQUEST, () -> service.update("m", "e", request));
        request.setUnableToVerifyReason("  Weather obstruction  ");
        assertEquals("Weather obstruction", service.update("m", "e", request).getUnableToVerifyReason());
        assertTrue(service.isReadyForSubmission(mission));
        error(ErrorCode.CHECKLIST_EXECUTION_TRANSITION_INVALID,
                () -> service.update("m", "e", request(ChecklistExecutionStatus.IN_PROGRESS)));
    }

    @Test void reverseIsRejectedUntilManagerRejectsResult() {
        service.update("m", "e", request(ChecklistExecutionStatus.COMPLETED));
        error(ErrorCode.CHECKLIST_EXECUTION_TRANSITION_INVALID, () -> service.update("m", "e", request(ChecklistExecutionStatus.PENDING)));
        var result = new MissionResult(); result.setApprovalStatus(MissionResultApprovalStatus.REJECTED);
        when(results.findByMissionId("m")).thenReturn(Optional.of(result));
        service.update("m", "e", request(ChecklistExecutionStatus.IN_PROGRESS));
        assertNull(execution.getCompletedAt());
    }

    @Test void pendingAndApprovedResultsLockAllMutations() {
        var result = new MissionResult(); when(results.findByMissionId("m")).thenReturn(Optional.of(result));
        for (var status : List.of(MissionResultApprovalStatus.PENDING_MANAGER_APPROVAL, MissionResultApprovalStatus.APPROVED)) {
            result.setApprovalStatus(status);
            error(ErrorCode.CHECKLIST_EXECUTION_LOCKED, () -> service.update("m", "e", request(ChecklistExecutionStatus.COMPLETED)));
        }
        verify(executions, never()).saveAndFlush(any());
    }

    @Test void staleInvalidAndOversizedRequestsAreRejected() {
        var request = request(ChecklistExecutionStatus.COMPLETED); request.setExpectedVersion(9L);
        error(ErrorCode.CONCURRENT_UPDATE, () -> service.update("m", "e", request));
        request.setExpectedVersion(null); error(ErrorCode.INVALID_REQUEST, () -> service.update("m", "e", request));
        request.setExpectedVersion(0L); request.setObservation("x".repeat(2001));
        error(ErrorCode.INVALID_REQUEST, () -> service.update("m", "e", request));
        request.setObservation(null); request.setUnableToVerifyReason("x".repeat(1001));
        error(ErrorCode.INVALID_REQUEST, () -> service.update("m", "e", request));
    }

    @Test void parentMismatchCrossOrderAndUnknownEntitiesAreDistinct() {
        execution.setMissionId("other"); error(ErrorCode.CHECKLIST_EXECUTION_PARENT_MISMATCH,
                () -> service.update("m", "e", request(ChecklistExecutionStatus.COMPLETED)));
        execution.setMissionId("m"); var other = new Order(); other.setId("other"); item.setOrder(other);
        error(ErrorCode.CHECKLIST_EXECUTION_INTEGRITY_INVALID, () -> service.update("m", "e", request(ChecklistExecutionStatus.COMPLETED)));
        error(ErrorCode.CHECKLIST_EXECUTION_NOT_FOUND, () -> service.update("m", "missing", request(ChecklistExecutionStatus.COMPLETED)));
        error(ErrorCode.MISSION_NOT_FOUND, () -> service.update("missing", "e", request(ChecklistExecutionStatus.COMPLETED)));
        error(ErrorCode.CHECKLIST_EXECUTION_INTEGRITY_INVALID, () -> service.initialize(mission));
    }

    @Test void missingRowsAreNotReadyAndReadsNeverBackfill() {
        when(executions.findOrderedByMissionId("m")).thenReturn(List.of());
        assertFalse(service.getByMissionId("m").readyForSubmission());
        error(ErrorCode.CHECKLIST_NOT_READY, () -> service.requireReadyForSubmission(mission));
        verify(executions, never()).saveAllAndFlush(any());
        when(items.findAllByOrderIdInOrderByDisplayOrderAscIdAsc(List.of("o"))).thenReturn(List.of());
        assertTrue(service.getByMissionId("m").legacySnapshot()); assertTrue(service.isReadyForSubmission(mission));
        order.setChecklistSnapshotAt(java.time.Instant.now()); assertFalse(service.getByMissionId("m").legacySnapshot());
    }

    @Test void serviceRechecksActorUnderMissionLock() {
        when(auth.canExecuteMonitoringChecklist("m")).thenReturn(false);
        error(ErrorCode.ACCESS_DENIED, () -> service.update("m", "e", request(ChecklistExecutionStatus.COMPLETED)));
        verify(executions, never()).saveAndFlush(any());
        when(auth.canViewMission("m")).thenReturn(false);
        error(ErrorCode.ACCESS_DENIED, () -> service.getByMissionId("m"));
    }
}
