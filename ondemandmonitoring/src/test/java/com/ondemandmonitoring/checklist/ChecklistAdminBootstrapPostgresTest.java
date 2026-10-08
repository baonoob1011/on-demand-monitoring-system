package com.ondemandmonitoring.checklist;

import com.ondemandmonitoring.checklist.config.ChecklistDefaultSeedInitializer;
import com.ondemandmonitoring.checklist.config.ChecklistDefaults;
import com.ondemandmonitoring.checklist.dto.request.ChecklistReorderRequest;
import com.ondemandmonitoring.checklist.dto.response.ServiceChecklistResponse;
import com.ondemandmonitoring.checklist.repository.*;
import com.ondemandmonitoring.common.exception.*;
import com.ondemandmonitoring.service.repository.ServiceRepository;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import static org.junit.jupiter.api.Assertions.*;

@SpringJUnitConfig(ChecklistAdminBootstrapPostgresTest.SeedConfig.class)
class ChecklistAdminBootstrapPostgresTest extends ChecklistPostgresTest {
    @Autowired @org.springframework.beans.factory.annotation.Qualifier("checklistSeed")
    org.springframework.boot.ApplicationRunner seed;

    ChecklistReorderRequest reorder(List<ServiceChecklistResponse> rows) {
        return new ChecklistReorderRequest(rows.stream().map(row -> new ChecklistReorderRequest.Item(row.getChecklistId(), row.getVersion())).toList());
    }

    @Test void seedRerunPersistsIdentityAndRestoresEmptySupportedTemplate() throws Exception {
        String service = service("Giám sát công trình");
        seed.run(null); var initial = assignments.getByService(service, false);
        assertEquals(6, initial.size()); assertEquals(19, definitions.count());
        seed.run(null); assertEquals(19, definitions.count()); assertEquals(6, links.count());
        var reversed = new ArrayList<>(initial); Collections.reverse(reversed); assignments.reorder(service, reorder(reversed));
        seed.run(null); assertEquals(reversed.stream().map(ServiceChecklistResponse::getChecklistId).toList(), assignments.getByService(service, false).stream().map(ServiceChecklistResponse::getChecklistId).toList());
        initial.forEach(row -> assignments.unassign(service, row.getChecklistId()));
        seed.run(null); assertEquals(6, assignments.getByService(service, false).size()); assertEquals(19, definitions.count());
    }

    @Test void seededSnapshotStaysImmutableAndNewOrderUsesChangedTemplate() throws Exception {
        String service = service("Giám sát công trình"); seed.run(null);
        var request = orderRequest(service);
        var old = orderService.createOrder(request);
        assertEquals(6, old.getChecklistItems().size());
        assertEquals(ChecklistDefaults.CONTENTS.getFirst(), old.getChecklistItems().getFirst().getContent());
        var template = assignments.getByService(service, false);
        template.subList(2, template.size()).forEach(row -> assignments.unassign(service, row.getChecklistId()));
        var remaining = new ArrayList<>(assignments.getByService(service, false)); Collections.reverse(remaining);
        assignments.reorder(service, reorder(remaining));
        var latest = orderService.createOrder(request);
        assertEquals(remaining.stream().map(ServiceChecklistResponse::getContent).toList(), latest.getChecklistItems().stream().map(row -> row.getContent()).toList());
        assertEquals(old.getChecklistItems().stream().map(row -> row.getContent()).toList(), orderService.getOrderById(old.getId()).getChecklistItems().stream().map(row -> row.getContent()).toList());
    }

    @Test void abcToBdChangesOnlyFutureOrders() {
        String service = service("Custom"), a = checklist("A"), b = checklist("B"), c = checklist("C"), d = checklist("D");
        assignments.assign(service, assignment(a, 0)); assignments.assign(service, assignment(b, 1)); assignments.assign(service, assignment(c, 2));
        var request = orderRequest(service);
        var old = orderService.createOrder(request);
        assignments.unassign(service, a); assignments.unassign(service, c); assignments.assign(service, assignment(d, 1));
        var latest = orderService.createOrder(request);
        assertEquals(List.of("A", "B", "C"), orderService.getOrderById(old.getId()).getChecklistItems().stream().map(row -> row.getContent()).toList());
        assertEquals(List.of("B", "D"), latest.getChecklistItems().stream().map(row -> row.getContent()).toList());
    }

    @Test void concurrentReorderRejectsStaleCommandWithoutDuplicatePositions() throws Exception {
        String service = service("Custom"), a = checklist("A"), b = checklist("B");
        assignments.assign(service, assignment(a, 0)); assignments.assign(service, assignment(b, 1));
        var rows = new ArrayList<>(assignments.getByService(service, false)); Collections.reverse(rows);
        var request = reorder(rows); var outcomes = race(() -> assignments.reorder(service, request));
        assertEquals(1, outcomes.stream().filter(ApiException.class::isInstance).count());
        assertEquals(List.of(0, 1), assignments.getByService(service, false).stream().map(ServiceChecklistResponse::getDisplayOrder).toList());
    }

    @Test void migrationIsRerunnableAndPreservesCustomizedTemplate() throws Exception {
        String service = service("Custom"), checklist = checklist("Admin"); assignments.assign(service, assignment(checklist, 0));
        var jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("alter table checklist_definitions drop column seed_code");
        jdbc.execute("alter table services drop column checklist_defaults_initialized");
        try (var resource = getClass().getClassLoader().getResourceAsStream("seeddata/migrations/add_checklist_bootstrap_markers.sql")) {
            assertNotNull(resource); String sql = new String(resource.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).replace("public.", SCHEMA + ".");
            jdbc.execute(sql); jdbc.execute(sql);
        }
        assertTrue(jdbc.queryForObject("select checklist_defaults_initialized from services where id=?", Boolean.class, service));
        assertEquals(1, links.count());
    }

    @Test void allKnownServiceDefinitionsReceiveSensibleDefaultsWithoutCreatingServices() throws Exception {
        ChecklistDefaults.templates().keySet().forEach(this::service);
        seed.run(null); seed.run(null);
        assertEquals(4, services.count()); assertEquals(19, definitions.count()); assertEquals(23, links.count());
        for (var service : services.findAll()) {
            var rows = assignments.getByService(service.getId(), false);
            assertEquals(java.util.stream.IntStream.range(0, rows.size()).boxed().toList(), rows.stream().map(ServiceChecklistResponse::getDisplayOrder).toList());
            assertEquals(ChecklistDefaults.templates().get(service.getName()).size(), rows.size());
            assertEquals(rows.size(), rows.stream().map(ServiceChecklistResponse::getChecklistId).distinct().count());
        }
    }

    @Configuration
    @Import(ChecklistPostgresTest.Config.class)
    static class SeedConfig {
        @Bean ChecklistDefaultSeedInitializer checklistSeed(ServiceRepository services, ChecklistDefinitionRepository definitions, ServiceChecklistRepository assignments) {
            return new ChecklistDefaultSeedInitializer(services, definitions, assignments);
        }
    }
}
