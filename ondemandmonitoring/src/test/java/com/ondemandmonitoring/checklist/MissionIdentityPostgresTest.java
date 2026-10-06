package com.ondemandmonitoring.checklist;

import com.ondemandmonitoring.order.enums.OrderStatus;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;

/** Sibling fixtures relax the gate only inside a rolled-back transaction in the isolated test schema. */
class MissionIdentityPostgresTest extends OrderMissionPostgresTest {
    @Test void aggregateQuerySupportsZeroOneAndMultipleChildrenWithStableOrdering() {
        String orderId = order(OrderStatus.APPROVED);
        assertTrue(missions.findAllByOrderIdOrderByScheduledStartAtAscIdAsc(orderId).isEmpty());
        var first = missionService.createMission(request(orderId));
        assertEquals(java.util.List.of(first.getId()), missions.findAllByOrderIdOrderByScheduledStartAtAscIdAsc(orderId)
                .stream().map(m -> m.getId()).toList());
        transaction.executeWithoutResult(tx -> {
            var jdbc = new JdbcTemplate(dataSource);
            // Restore all DDL and data by rollback; never remove the runtime gate in the project DB.
            jdbc.execute("ALTER TABLE missions DROP CONSTRAINT uk_missions_order");
            jdbc.update("UPDATE missions SET scheduled_start_at=? WHERE id=?",
                    java.sql.Timestamp.from(Instant.parse("2026-10-08T10:00:00Z")), first.getId());
            for (String id : java.util.List.of("sibling-b", "sibling-a")) {
                jdbc.update("""
                        INSERT INTO missions(id,mission_code,order_id,status,scheduled_start_at,scheduled_end_at,created_at,version)
                        VALUES (?, ?, ?, 'RESOURCE_ASSIGNING', '2026-10-08T09:00:00Z', '2026-10-08T11:00:00Z', now(), 0)
                        """, id, id, orderId);
            }
            assertEquals(java.util.List.of("sibling-a", "sibling-b", first.getId()),
                    missions.findAllByOrderIdOrderByScheduledStartAtAscIdAsc(orderId).stream().map(m -> m.getId()).toList());
            assertEquals("sibling-b", missions.findById("sibling-b").orElseThrow().getId());
            assertTrue(missions.findById(orderId).isEmpty());
            tx.setRollbackOnly();
        });
        assertEquals(1, count(orderId));
    }
}
