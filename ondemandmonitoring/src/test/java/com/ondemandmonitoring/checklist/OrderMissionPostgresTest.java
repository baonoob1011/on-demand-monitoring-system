package com.ondemandmonitoring.checklist;

import com.ondemandmonitoring.common.exception.ApiException;
import com.ondemandmonitoring.common.exception.ErrorCode;
import com.ondemandmonitoring.mission.controller.MissionController;
import com.ondemandmonitoring.mission.controller.OrderMissionController;
import com.ondemandmonitoring.mission.dto.request.MissionCreateRequest;
import com.ondemandmonitoring.mission.dto.response.MissionResponse;
import com.ondemandmonitoring.mission.mapper.MissionMapper;
import com.ondemandmonitoring.mission.repository.MissionRepository;
import com.ondemandmonitoring.mission.service.IMissionService;
import com.ondemandmonitoring.mission.service.impl.MissionService;
import com.ondemandmonitoring.order.enums.OrderStatus;
import com.ondemandmonitoring.order.repository.OrderRepository;
import jakarta.persistence.EntityManagerFactory;
import java.util.UUID;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Reuses the opt-in, isolated PostgreSQL fixture and its Order snapshot regressions. */
@SpringJUnitConfig(OrderMissionPostgresTest.MissionConfig.class)
class OrderMissionPostgresTest extends ChecklistPostgresTest {
    @Autowired MissionRepository missions;
    @Autowired IMissionService missionService;

    @Override @BeforeEach void prepare() {
        new JdbcTemplate(dataSource).execute("TRUNCATE missions CASCADE");
        super.prepare();
    }

    String order(OrderStatus status) {
        var response = orderService.createOrder(orderRequest(service("Monitoring")));
        transaction.executeWithoutResult(tx -> orders.findById(response.getId()).orElseThrow().setOrderStatus(status));
        return response.getId();
    }

    MissionCreateRequest request(String orderId) {
        var request = new MissionCreateRequest();
        request.setOrderId(orderId);
        var start = java.time.LocalDate.now().plusDays(1).atStartOfDay(java.time.ZoneOffset.UTC).toInstant();
        request.setScheduledStartAt(start);
        request.setScheduledEndAt(start.plusSeconds(3600));
        return request;
    }

    long count(String orderId) {
        return new JdbcTemplate(dataSource).queryForObject(
                "SELECT count(*) FROM missions WHERE order_id = ?", Long.class, orderId);
    }

    @Test void firstAndSequentialCreationReturnSameMission() {
        String id = order(OrderStatus.APPROVED);
        var first = missionService.createMission(request(id));
        var created = missions.findById(first.getId()).orElseThrow();
        assertNotNull(created.getOrder());
        assertEquals(id, created.getOrder().getId());
        assertEquals(first.getId(), missionService.createMission(request(id)).getId());
        assertEquals(1, count(id));
        // Retry still returns the existing execution after Order lifecycle advances.
        transaction.executeWithoutResult(tx -> orders.findById(id).orElseThrow().setOrderStatus(OrderStatus.IN_PROGRESS));
        assertEquals(first.getId(), missionService.createMission(request(id)).getId());
    }

    @Test void bothCreationControllersUseSamePersistedMission() {
        String id = order(OrderStatus.APPROVED);
        var general = new MissionController(missionService, null, null);
        var nested = new OrderMissionController(missionService);
        var first = general.createMission(request(id)).getBody().getData();
        var second = nested.createMissionForOrder(id, request("ignored-body-id")).getBody().getData();
        assertEquals(first.getId(), second.getId());
        assertEquals(1, count(id));
    }

    @Test void pendingOrderCannotCreateMission() {
        String id = order(OrderStatus.PENDING);
        assertEquals(ErrorCode.INVALID_REQUEST,
                assertThrows(ApiException.class, () -> missionService.createMission(request(id))).getErrorCode());
        assertEquals(0, count(id));
    }

    @Test void databaseRejectsDuplicateEvenWhenServiceIsBypassed() {
        String id = order(OrderStatus.APPROVED);
        missionService.createMission(request(id));
        var jdbc = new JdbcTemplate(dataSource);
        var exception = assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("""
                INSERT INTO missions(id, mission_code, order_id, status, scheduled_start_at, scheduled_end_at, created_at, version)
                VALUES (?, ?, ?, 'RESOURCE_ASSIGNING', now(), now() + interval '1 hour', now(), 0)
                """, UUID.randomUUID().toString(), "duplicate-code", id));
        // Hibernate may generate an implicit OneToOne UNIQUE name; assert the
        // actual violation and column rather than relying on the DDL naming strategy.
        assertEquals("23505", ((java.sql.SQLException) exception.getMostSpecificCause()).getSQLState());
        assertTrue(exception.getMostSpecificCause().getMessage().contains("(order_id)"));
        assertEquals(1, count(id));
    }

    @Test void activeMappingIsMandatoryLazyUnidirectionalOneToOne() throws Exception {
        var field = com.ondemandmonitoring.mission.domain.Mission.class.getDeclaredField("order");
        var mapping = field.getAnnotation(jakarta.persistence.OneToOne.class);
        assertNotNull(mapping);
        assertNull(field.getAnnotation(jakarta.persistence.ManyToOne.class));
        assertEquals(jakarta.persistence.FetchType.LAZY, mapping.fetch());
        assertFalse(mapping.optional());
        assertEquals(0, mapping.cascade().length);
        assertFalse(mapping.orphanRemoval());
        assertEquals("", mapping.mappedBy());
        var join = field.getAnnotation(jakarta.persistence.JoinColumn.class);
        assertFalse(join.nullable()); assertTrue(join.unique());
        assertTrue(java.util.Arrays.stream(com.ondemandmonitoring.order.domain.Order.class.getDeclaredFields())
                .noneMatch(f -> f.getType() == com.ondemandmonitoring.mission.domain.Mission.class));
        var attribute = entityManagerFactory.getMetamodel().entity(com.ondemandmonitoring.mission.domain.Mission.class)
                .getSingularAttribute("order");
        assertEquals(jakarta.persistence.metamodel.Attribute.PersistentAttributeType.ONE_TO_ONE,
                attribute.getPersistentAttributeType());
        assertFalse(attribute.isOptional());
        String id = order(OrderStatus.APPROVED);
        String missionId = missionService.createMission(request(id)).getId();
        transaction.executeWithoutResult(tx -> {
            var mission = missions.findById(missionId).orElseThrow();
            assertFalse(org.hibernate.Hibernate.isInitialized(mission.getOrder()));
            assertEquals(id, mission.getOrder().getId());
            assertEquals("Snapshot order", mission.getOrder().getTitle());
        });
    }

    @Test void postgresRejectsNullOrderAndPreservesForeignKeyLifecycle() {
        var jdbc = new JdbcTemplate(dataSource);
        // JDBC bypasses both Hibernate nullability checks and Bean Validation.
        var exception = assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("""
                INSERT INTO missions(id, mission_code, order_id, status, scheduled_start_at, scheduled_end_at, created_at, version)
                VALUES (?, 'missing-order', NULL, 'RESOURCE_ASSIGNING', now(), now() + interval '1 hour', now(), 0)
                """, UUID.randomUUID().toString()));
        assertEquals("23502", ((java.sql.SQLException) exception.getMostSpecificCause()).getSQLState());
        assertTrue(exception.getMostSpecificCause().getMessage().contains("order_id"));
        var missingOrder = assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("""
                INSERT INTO missions(id, mission_code, order_id, status, scheduled_start_at, scheduled_end_at, created_at, version)
                VALUES (?, 'invalid-order', 'nonexistent-order', 'RESOURCE_ASSIGNING', now(), now() + interval '1 hour', now(), 0)
                """, UUID.randomUUID().toString()));
        assertEquals("23503", ((java.sql.SQLException) missingOrder.getMostSpecificCause()).getSQLState());
        assertTrue(missingOrder.getMostSpecificCause().getMessage().contains("order_id"));
        String id = order(OrderStatus.APPROVED);
        missionService.createMission(request(id));
        var fkViolation = assertThrows(DataIntegrityViolationException.class,
                () -> jdbc.update("DELETE FROM orders WHERE id = ?", id));
        assertEquals("23503", ((java.sql.SQLException) fkViolation.getMostSpecificCause()).getSQLState());
        assertEquals(1, count(id));
    }

    @Test void manualMigrationIsSafeRepeatableAndRefusesExistingDuplicates() throws Exception {
        String schema = SCHEMA + "_migration";
        var jdbc = new JdbcTemplate(dataSource);
        String sql = new org.springframework.core.io.ClassPathResource(
                "seeddata/migrations/enforce_order_mission_one_to_one.sql")
                .getContentAsString(java.nio.charset.StandardCharsets.UTF_8).replace("public.", schema + ".");
        jdbc.execute("CREATE SCHEMA " + schema);
        try {
            jdbc.execute("CREATE TABLE " + schema + ".orders(id varchar PRIMARY KEY)");
            jdbc.update("INSERT INTO " + schema + ".orders VALUES ('order-x')");
            jdbc.execute("CREATE TABLE " + schema + ".missions(id varchar PRIMARY KEY, order_id varchar REFERENCES "
                    + schema + ".orders(id))");
            // Empty database, then repeat on an existing database with data: no loss.
            jdbc.execute(sql);
            assertEquals("NO", jdbc.queryForObject("SELECT is_nullable FROM information_schema.columns "
                    + "WHERE table_schema = ? AND table_name = 'missions' AND column_name = 'order_id'", String.class, schema));
            jdbc.update("INSERT INTO " + schema + ".missions VALUES ('first', 'order-x')");
            jdbc.execute(sql);
            assertThrows(DataIntegrityViolationException.class,
                    () -> jdbc.update("INSERT INTO " + schema + ".missions VALUES ('second', 'order-x')"));
            jdbc.execute("ALTER TABLE " + schema + ".missions DROP CONSTRAINT uk_missions_order");
            jdbc.update("INSERT INTO " + schema + ".missions VALUES ('second', 'order-x')");
            var duplicateGuard = assertThrows(org.springframework.dao.DataAccessException.class, () -> jdbc.execute(sql));
            assertTrue(duplicateGuard.getMostSpecificCause().getMessage().contains("Duplicate missions per Order"));
            assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM " + schema + ".missions", Integer.class));
            assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM pg_constraint WHERE conrelid = '"
                    + schema + ".missions'::regclass AND contype = 'u'", Integer.class));
            // A NULL legacy row must also abort without silently repairing data.
            jdbc.execute("DELETE FROM " + schema + ".missions");
            jdbc.execute("ALTER TABLE " + schema + ".missions ALTER COLUMN order_id DROP NOT NULL");
            jdbc.update("INSERT INTO " + schema + ".missions VALUES ('without-order', NULL)");
            var nullGuard = assertThrows(org.springframework.dao.DataAccessException.class, () -> jdbc.execute(sql));
            assertTrue(nullGuard.getMostSpecificCause().getMessage().contains("Missions without an Order"));
            assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM " + schema
                    + ".missions WHERE order_id IS NULL", Integer.class));
            assertEquals("YES", jdbc.queryForObject("SELECT is_nullable FROM information_schema.columns "
                    + "WHERE table_schema = ? AND table_name = 'missions' AND column_name = 'order_id'", String.class, schema));
            assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM pg_constraint WHERE conrelid = '"
                    + schema + ".missions'::regclass AND contype = 'u'", Integer.class));
        } finally {
            // Explicit generated schema owned by this test only; never public or the database itself.
            jdbc.execute("DROP SCHEMA " + schema + " CASCADE");
        }
    }

    @Test void concurrentCreationBlocksOnOrderAndReturnsSameMission() throws Exception {
        String id = order(OrderStatus.APPROVED);
        var locked = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(3)) {
            Future<?> holder = pool.submit(() -> transaction.executeWithoutResult(tx -> {
                orders.findByIdForUpdate(id).orElseThrow(); locked.countDown();
                try { assertTrue(release.await(10, TimeUnit.SECONDS)); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
            }));
            assertTrue(locked.await(5, TimeUnit.SECONDS));
            Future<MissionResponse> first = pool.submit(() -> missionService.createMission(request(id)));
            Future<MissionResponse> second = pool.submit(() -> missionService.createMission(request(id)));
            // Observe two actual PostgreSQL sessions waiting on a database lock, not a sequential fake race.
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            int waiting = 0;
            var jdbc = new JdbcTemplate(dataSource);
            while (System.nanoTime() < deadline && waiting < 2) {
                waiting = jdbc.queryForObject("""
                        SELECT count(*) FROM pg_stat_activity
                        WHERE datname = current_database() AND usename = current_user
                          AND wait_event_type = 'Lock' AND query LIKE '%orders%' AND query LIKE '%for%'
                        """, Integer.class);
                Thread.sleep(20);
            }
            assertEquals(2, waiting);
            release.countDown(); holder.get(10, TimeUnit.SECONDS);
            assertEquals(first.get(10, TimeUnit.SECONDS).getId(), second.get(10, TimeUnit.SECONDS).getId());
            assertEquals(1, count(id));
        } finally { release.countDown(); }
    }

    @Configuration @Import(ChecklistPostgresTest.Config.class)
    static class MissionConfig {
        @Bean MissionRepository missions(EntityManagerFactory factory) {
            return new JpaRepositoryFactory(SharedEntityManagerCreator.createSharedEntityManager(factory))
                    .getRepository(MissionRepository.class);
        }
        @Bean IMissionService missionService(MissionRepository missions, OrderRepository orders) {
            var mapper = mock(MissionMapper.class);
            when(mapper.toResponse(any())).thenAnswer(call -> {
                com.ondemandmonitoring.mission.domain.Mission mission = call.getArgument(0);
                return MissionResponse.builder().id(mission.getId()).missionCode(mission.getMissionCode())
                        .status(mission.getStatus()).build();
            });
            // Unused lifecycle collaborators are not loaded: only the real transactional creation path is exercised.
            return new MissionService(null, missions, null, null, mapper, null, null, null, null, null,
                    null, null, orders, null, null, null, null, null, null, null, null, null, null, null);
        }
    }
}
