package com.ondemandmonitoring.mission.controller;

import com.ondemandmonitoring.mission.dto.request.MissionCreateRequest;
import com.ondemandmonitoring.mission.dto.response.MissionResponse;
import com.ondemandmonitoring.mission.service.IMissionService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderMissionControllerTest {

    private final IMissionService missionService = mock(IMissionService.class);
    private final OrderMissionController controller = new OrderMissionController(missionService,
            mock(com.ondemandmonitoring.mission.service.IOrderMissionQueryService.class));

    @Test
    void createMissionForOrderUsesPathOrderId() {
        MissionCreateRequest request = new MissionCreateRequest();
        request.setScheduledStartAt(Instant.parse("2026-09-30T01:00:00Z"));
        request.setScheduledEndAt(Instant.parse("2026-09-30T03:58:00Z"));
        MissionResponse response = MissionResponse.builder().id("mission-1").build();
        when(missionService.createMissionWithOutcome(request)).thenReturn(new com.ondemandmonitoring.mission.dto.response.MissionCreationResult(response, true));

        var result = controller.createMissionForOrder("order-1", request);

        assertEquals(response, result.getBody().getData());
        ArgumentCaptor<MissionCreateRequest> captor = ArgumentCaptor.forClass(MissionCreateRequest.class);
        verify(missionService).createMissionWithOutcome(captor.capture());
        assertEquals("order-1", captor.getValue().getOrderId());
    }
}
