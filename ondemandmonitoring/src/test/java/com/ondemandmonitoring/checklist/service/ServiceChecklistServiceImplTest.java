package com.ondemandmonitoring.checklist.service;

import com.ondemandmonitoring.checklist.domain.*;
import com.ondemandmonitoring.checklist.dto.request.ChecklistAssignmentRequest;
import com.ondemandmonitoring.checklist.mapper.ServiceChecklistMapper;
import com.ondemandmonitoring.checklist.repository.*;
import com.ondemandmonitoring.checklist.service.impl.ServiceChecklistServiceImpl;
import com.ondemandmonitoring.common.exception.*;
import com.ondemandmonitoring.service.domain.Service;
import com.ondemandmonitoring.service.repository.ServiceRepository;
import org.junit.jupiter.api.*;
import org.mapstruct.factory.Mappers;
import java.util.Optional;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class ServiceChecklistServiceImplTest {
    ServiceRepository services;
    ChecklistDefinitionRepository checklists;
    ServiceChecklistRepository assignments;
    ServiceChecklistServiceImpl useCase;
    Service service;
    ChecklistDefinition checklist;
    @BeforeEach void setup() {
        services = mock(ServiceRepository.class);
        checklists = mock(ChecklistDefinitionRepository.class);
        assignments = mock(ServiceChecklistRepository.class);
        useCase = new ServiceChecklistServiceImpl(services, checklists, assignments,
                Mappers.getMapper(ServiceChecklistMapper.class));
        service = Service.builder().name("Service").isActive(true).build(); service.setId("s1");
        checklist = new ChecklistDefinition(); checklist.setId("c1"); checklist.setContent("Check exits");
        when(services.findByIdForUpdate("s1")).thenReturn(Optional.of(service));
        when(checklists.findByIdForUpdate("c1")).thenReturn(Optional.of(checklist));
        when(assignments.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
    }
    ChecklistAssignmentRequest request(String id) {
        var request = new ChecklistAssignmentRequest(); request.setChecklistId(id); return request;
    }
    ServiceChecklist link() {
        var link = new ServiceChecklist(); link.setService(service); link.setChecklist(checklist); return link;
    }
    @Test void assignsAndMapsWithConsistentLockOrder() {
        var result = useCase.assign("s1", request("c1"));
        assertEquals("s1", result.getServiceId()); assertEquals("Check exits", result.getContent());
        var order = inOrder(services, checklists);
        order.verify(services).findByIdForUpdate("s1");
        order.verify(checklists).findByIdForUpdate("c1");
    }
    @Test void oneChecklistCanBeAssignedToMultipleServices() {
        var other = Service.builder().name("Other").isActive(true).build(); other.setId("s2");
        when(services.findByIdForUpdate("s2")).thenReturn(Optional.of(other));
        useCase.assign("s1", request("c1")); useCase.assign("s2", request("c1"));
        verify(assignments, times(2)).saveAndFlush(any());
    }
    @Test void oneServiceCanHaveMultipleChecklists() {
        var other = new ChecklistDefinition(); other.setId("c2"); other.setContent("Check PPE");
        when(checklists.findByIdForUpdate("c2")).thenReturn(Optional.of(other));
        useCase.assign("s1", request("c1")); useCase.assign("s1", request("c2"));
        verify(assignments, times(2)).saveAndFlush(any());
    }
    @Test void duplicateIsConflict() {
        when(assignments.existsByServiceIdAndChecklistId("s1", "c1")).thenReturn(true);
        assertEquals(ErrorCode.SERVICE_CHECKLIST_ALREADY_EXISTS,
                assertThrows(ApiException.class, () -> useCase.assign("s1", request("c1"))).getErrorCode());
        verify(assignments, never()).saveAndFlush(any());
    }
    @Test void rejectsInactiveParents() {
        service.setIsActive(false);
        assertEquals(ErrorCode.SERVICE_INACTIVE,
                assertThrows(ApiException.class, () -> useCase.assign("s1", request("c1"))).getErrorCode());
        service.setIsActive(true); checklist.setIsActive(false);
        assertEquals(ErrorCode.CHECKLIST_INACTIVE,
                assertThrows(ApiException.class, () -> useCase.assign("s1", request("c1"))).getErrorCode());
    }
    @Test void rejectsMissingParents() {
        assertEquals(ErrorCode.SERVICE_NOT_FOUND,
                assertThrows(ApiException.class, () -> useCase.assign("missing", request("c1"))).getErrorCode());
        assertEquals(ErrorCode.CHECKLIST_NOT_FOUND,
                assertThrows(ApiException.class, () -> useCase.assign("s1", request("missing"))).getErrorCode());
    }
    @Test void unassignRemovesOnlyRelationshipEvenWhenInactive() {
        var link = link(); service.setIsActive(false); checklist.setIsActive(false);
        when(assignments.findByServiceIdAndChecklistId("s1", "c1")).thenReturn(Optional.of(link));
        useCase.unassign("s1", "c1");
        verify(assignments).delete(link);
        verify(services, never()).delete(any(Service.class));
        verify(checklists, never()).delete(any(ChecklistDefinition.class));
    }
    @Test void nonexistentUnassignIs404() {
        assertEquals(ErrorCode.SERVICE_CHECKLIST_NOT_FOUND,
                assertThrows(ApiException.class, () -> useCase.unassign("s1", "c1")).getErrorCode());
    }
    @Test void orderCanChangeAndCannotBeNegative() {
        var link = link();
        when(assignments.findByServiceIdAndChecklistId("s1", "c1")).thenReturn(Optional.of(link));
        assertEquals(3, useCase.updateOrder("s1", "c1", 3).getDisplayOrder());
        assertThrows(ApiException.class, () -> useCase.updateOrder("s1", "c1", -1));
    }

    @Test void customerReadsOnlyActiveTemplateWhileAdminSeesInactive() {
        when(services.findById("s1")).thenReturn(Optional.of(service));
        when(assignments.findAllByServiceIdAndChecklistIsActiveTrueOrderByDisplayOrderAscIdAsc("s1"))
                .thenReturn(List.of(link()));
        checklist.setIsActive(false);
        when(assignments.findAllByServiceIdOrderByDisplayOrderAscIdAsc("s1")).thenReturn(List.of(link()));
        assertEquals(1, useCase.getByService("s1", true).size());
        assertFalse(useCase.getByService("s1", false).getFirst().getChecklistActive());
    }

    @Test void customerCannotLoadInactiveServiceButAdminCan() {
        service.setIsActive(false);
        when(services.findById("s1")).thenReturn(Optional.of(service));
        assertEquals(ErrorCode.SERVICE_INACTIVE,
                assertThrows(ApiException.class, () -> useCase.getByService("s1", true)).getErrorCode());
        assertTrue(useCase.getByService("s1", false).isEmpty());
    }

    @Test void emptyTemplateAndReverseLookupAreSupported() {
        when(services.findById("s1")).thenReturn(Optional.of(service));
        assertTrue(useCase.getByService("s1", true).isEmpty());
        when(checklists.existsById("c1")).thenReturn(true);
        when(assignments.findAllByChecklistIdOrderByServiceNameAscServiceIdAsc("c1")).thenReturn(List.of(link()));
        assertEquals("s1", useCase.getByChecklist("c1").getFirst().getServiceId());
        assertEquals(ErrorCode.CHECKLIST_NOT_FOUND,
                assertThrows(ApiException.class, () -> useCase.getByChecklist("missing")).getErrorCode());
    }
}
