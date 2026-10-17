package com.ondemandmonitoring.mission.mapper;

import static org.junit.jupiter.api.Assertions.*;

import com.ondemandmonitoring.checklist.domain.ChecklistDefinition;
import com.ondemandmonitoring.mission.domain.MissionChecklistAssignment;
import com.ondemandmonitoring.mission.domain.MissionChecklistExecution;
import com.ondemandmonitoring.order.domain.OrderChecklistItem;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

class MissionChecklistExecutionMapperGuidanceTest {
    private final MissionChecklistExecutionMapper mapper = Mappers.getMapper(MissionChecklistExecutionMapper.class);

    @Test void crewSeesTheGuidanceSnapshottedOnTheOrderNotTheCatalog() {
        var catalog = new ChecklistDefinition();
        catalog.setDescription("Catalog text edited later");
        catalog.setExecutionSteps(List.of("Catalog step"));
        var item = new OrderChecklistItem();
        item.setContent("Kiểm tra mái");
        item.setSourceChecklist(catalog);
        item.setDescriptionSnapshot("Snapshot text");
        item.setExecutionStepsSnapshot(List.of("Bay lưới song song", "Chụp cận"));
        item.setInputRequirementsSnapshot(List.of("Trời quang"));
        item.setExpectedOutputsSnapshot(List.of("Ảnh toàn mái"));
        var assignment = new MissionChecklistAssignment();
        var execution = new MissionChecklistExecution();
        execution.setOrderChecklistItem(item);
        execution.setMissionChecklistAssignment(assignment);

        var response = mapper.toResponse(execution);

        assertEquals("Snapshot text", response.getDescription());
        assertEquals(List.of("Bay lưới song song", "Chụp cận"), response.getExecutionSteps());
        assertEquals(List.of("Trời quang"), response.getInputRequirements());
        assertEquals(List.of("Ảnh toàn mái"), response.getExpectedOutputs());
    }

    @Test void itemsWithoutGuidanceMapToNull() {
        var item = new OrderChecklistItem();
        item.setContent("Mục tùy chỉnh");
        var execution = new MissionChecklistExecution();
        execution.setOrderChecklistItem(item);
        execution.setMissionChecklistAssignment(new MissionChecklistAssignment());

        var response = mapper.toResponse(execution);

        assertNull(response.getDescription());
        assertNull(response.getExecutionSteps());
    }
}
