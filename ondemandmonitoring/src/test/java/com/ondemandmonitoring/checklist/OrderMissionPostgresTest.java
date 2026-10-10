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
import com.ondemandmonitoring.order.repository.OrderChecklistItemRepository;
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
        request.setIdempotencyKey(UUID.nameUUIDFromBytes(orderId.getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString());
        request.setExpectedPlanRevision(0L);
        request.setScopeLabel("Whole Order scope");
        var start = java.time.LocalDate.now().plusDays(1).atStartOfDay(java.time.ZoneOffset.UTC).toInstant();
        request.setScheduledStartAt(start);
        request.setScheduledEndAt(start.plusSeconds(3600));
        return request;
    }

    /** Explicit historical fixture, not the new planning creation command. */
    MissionResponse legacyMission(MissionCreateRequest request) {
        return transaction.execute(tx -> {
            var mission = new com.ondemandmonitoring.mission.domain.Mission();
            mission.setOrder(orders.findById(request.getOrderId()).orElseThrow());
            mission.setMissionCode("legacy-" + UUID.randomUUID());
            mission.setStatus(com.ondemandmonitoring.mission.enums.MissionStatus.RESOURCE_ASSIGNING);
            mission.setTargetPoint(mission.getOrder().getPoint());
            mission.setTargetArea(mission.getOrder().getTargetArea());
            mission.setScheduledStartAt(request.getScheduledStartAt());
            mission.setScheduledEndAt(request.getScheduledEndAt());
            mission = missions.saveAndFlush(mission);
            var executions = new org.springframework.data.jpa.repository.support.JpaRepositoryFactory(
                    org.springframework.orm.jpa.SharedEntityManagerCreator.createSharedEntityManager(entityManagerFactory))
                    .getRepository(com.ondemandmonitoring.mission.repository.MissionChecklistExecutionRepository.class);
            var assignmentRepository = new JpaRepositoryFactory(SharedEntityManagerCreator.createSharedEntityManager(entityManagerFactory))
                    .getRepository(com.ondemandmonitoring.mission.repository.MissionChecklistAssignmentRepository.class);
            // Explicit historical fixture only: production materializes selected Assignments, not whole Orders.
            for (var item : snapshots.findAllByOrderIdInOrderByDisplayOrderAscIdAsc(java.util.List.of(mission.getOrder().getId()))) {
                var assignment = new com.ondemandmonitoring.mission.domain.MissionChecklistAssignment();
                assignment.setMissionId(mission.getId()); assignment.setOrderId(mission.getOrder().getId());
                assignment.setOrderChecklistItemId(item.getId()); assignment.setOrderChecklistItem(item);
                assignment.setDisplayOrder(item.getDisplayOrder()); assignment.setAssignedAt(java.time.Instant.now());
                assignmentRepository.saveAndFlush(assignment);
            }
            new com.ondemandmonitoring.mission.service.impl.MissionChecklistMaterializationServiceImpl(assignmentRepository, executions).materialize(mission);
            return MissionResponse.builder().id(mission.getId()).build();
        });
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
        var nested = new OrderMissionController(missionService, null);
        var first = general.createMission(request(id)).getBody().getData();
        var retry = request(id); retry.setOrderId("ignored-body-id");
        var second = nested.createMissionForOrder(id, retry).getBody().getData();
        assertEquals(first.getId(), second.getId());
        assertEquals(1, count(id));
    }

    @Test void pendingOrderCannotCreateMission() {
        String id = order(OrderStatus.PENDING);
        assertEquals(ErrorCode.INVALID_REQUEST,
                assertThrows(ApiException.class, () -> missionService.createMission(request(id))).getErrorCode());
        assertEquals(0, count(id));
    }

    @Test void databaseRejectsDuplicateCreationKeyButAllowsSiblingMissions() {
        String id = order(OrderStatus.APPROVED);
        missionService.createMission(request(id));
        var jdbc = new JdbcTemplate(dataSource);
        var exception = assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("""
                INSERT INTO missions(id,mission_code,order_id,status,scheduled_start_at,scheduled_end_at,
                    created_at,version,creation_idempotency_key,creation_request_hash)
                SELECT ?,?,order_id,status,scheduled_start_at,scheduled_end_at,now(),0,
                    creation_idempotency_key,creation_request_hash FROM missions WHERE order_id=?
                """, UUID.randomUUID().toString(), "duplicate-code", id));
        assertEquals("23505", ((java.sql.SQLException) exception.getMostSpecificCause()).getSQLState());
        assertTrue(exception.getMostSpecificCause().getMessage().contains("uk_mission_creation_command"));
        var sibling = request(id);
        sibling.setIdempotencyKey(UUID.randomUUID().toString());
        sibling.setExpectedPlanRevision(1L);
        assertNotEquals(missions.findAllByOrderIdOrderByScheduledStartAtAscIdAsc(id).getFirst().getId(),
                missionService.createMission(sibling).getId());
        assertEquals(2, count(id));
    }

    @Test void mappingIsMandatoryLazyManyToOne() throws Exception {
        var field = com.ondemandmonitoring.mission.domain.Mission.class.getDeclaredField("order");
        var mapping = field.getAnnotation(jakarta.persistence.ManyToOne.class);
        assertNotNull(mapping);
        assertNull(field.getAnnotation(jakarta.persistence.OneToOne.class));
        assertEquals(jakarta.persistence.FetchType.LAZY, mapping.fetch());
        assertFalse(mapping.optional());
        assertEquals(0, mapping.cascade().length);
        var join = field.getAnnotation(jakarta.persistence.JoinColumn.class);
        assertFalse(join.nullable()); assertFalse(join.unique());
        assertTrue(java.util.Arrays.stream(com.ondemandmonitoring.order.domain.Order.class.getDeclaredFields())
                .noneMatch(f -> f.getType() == com.ondemandmonitoring.mission.domain.Mission.class));
        var attribute = entityManagerFactory.getMetamodel().entity(com.ondemandmonitoring.mission.domain.Mission.class)
                .getSingularAttribute("order");
        assertEquals(jakarta.persistence.metamodel.Attribute.PersistentAttributeType.MANY_TO_ONE,
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
        @Bean IMissionService missionService(MissionRepository missions, OrderRepository orders,
                com.ondemandmonitoring.mission.service.IMissionChecklistExecutionService executions) {
            var mapper = mock(MissionMapper.class);
            when(mapper.toResponse(any())).thenAnswer(call -> {
                com.ondemandmonitoring.mission.domain.Mission mission = call.getArgument(0);
                return MissionResponse.builder().id(mission.getId()).missionCode(mission.getMissionCode())
                        .status(mission.getStatus()).build();
            });
            // Unused lifecycle collaborators are not loaded: only the real transactional creation path is exercised.
            return new MissionService(null, missions, null, null, mapper, null, null, null, null, null,
                    null, null, orders, null, null, null, null, null, null, null, null, null, null, null, executions,
                    org.mockito.Mockito.mock(com.ondemandmonitoring.mission.service.IResourceReservationService.class), null);
        }
        @Bean com.ondemandmonitoring.mission.repository.MissionChecklistExecutionRepository executions(EntityManagerFactory factory) {
            return new JpaRepositoryFactory(SharedEntityManagerCreator.createSharedEntityManager(factory))
                    .getRepository(com.ondemandmonitoring.mission.repository.MissionChecklistExecutionRepository.class);
        }
        @Bean @Primary com.ondemandmonitoring.mission.repository.MissionResultRepository executionResults(EntityManagerFactory factory) {
            return new JpaRepositoryFactory(SharedEntityManagerCreator.createSharedEntityManager(factory))
                    .getRepository(com.ondemandmonitoring.mission.repository.MissionResultRepository.class);
        }
        @Bean com.ondemandmonitoring.mission.service.IMissionAuthorizationService missionAuthorization() {
            var auth = mock(com.ondemandmonitoring.mission.service.IMissionAuthorizationService.class);
            when(auth.canViewMission(anyString())).thenReturn(true);
            when(auth.canExecuteMonitoringChecklist(anyString())).thenReturn(true);
            return auth;
        }
        @Bean com.ondemandmonitoring.mission.service.IMissionChecklistExecutionService executionService(
                MissionRepository missions, com.ondemandmonitoring.mission.service.IMissionChecklistScopeService scope,
                com.ondemandmonitoring.mission.repository.MissionChecklistExecutionRepository executions,
                com.ondemandmonitoring.mission.repository.MissionResultRepository results,
                com.ondemandmonitoring.mission.service.IMissionAuthorizationService auth,
                com.ondemandmonitoring.user.service.AuthenticatedUserResolver resolver,
                com.ondemandmonitoring.mission.service.IChecklistEvidenceService evidenceService) {
            return new com.ondemandmonitoring.mission.service.impl.MissionChecklistExecutionServiceImpl(
                    missions, scope, executions, results, auth, resolver,
                    org.mapstruct.factory.Mappers.getMapper(com.ondemandmonitoring.mission.mapper.MissionChecklistExecutionMapper.class), evidenceService);
        }
        @Bean com.ondemandmonitoring.mission.repository.MissionChecklistAssignmentRepository missionAssignments(EntityManagerFactory factory) {
            return new JpaRepositoryFactory(SharedEntityManagerCreator.createSharedEntityManager(factory)).getRepository(com.ondemandmonitoring.mission.repository.MissionChecklistAssignmentRepository.class);
        }
        @Bean com.ondemandmonitoring.mission.service.IMissionChecklistScopeService checklistScope(
                com.ondemandmonitoring.mission.repository.MissionChecklistAssignmentRepository assignments,
                com.ondemandmonitoring.mission.repository.MissionChecklistExecutionRepository executions) {
            return new com.ondemandmonitoring.mission.service.impl.MissionChecklistScopeServiceImpl(assignments, executions);
        }
        @Bean com.ondemandmonitoring.mission.repository.MissionChecklistEvidenceRepository evidenceRepository(EntityManagerFactory factory) {
            return new JpaRepositoryFactory(SharedEntityManagerCreator.createSharedEntityManager(factory)).getRepository(com.ondemandmonitoring.mission.repository.MissionChecklistEvidenceRepository.class);
        }
        @Bean com.ondemandmonitoring.media.repository.MediaAssetRepository mediaRepository(EntityManagerFactory factory) {
            return new JpaRepositoryFactory(SharedEntityManagerCreator.createSharedEntityManager(factory)).getRepository(com.ondemandmonitoring.media.repository.MediaAssetRepository.class);
        }
        @Bean com.ondemandmonitoring.media.service.IMediaObjectStorage evidenceStorage() {
            return mock(com.ondemandmonitoring.media.service.IMediaObjectStorage.class);
        }
        @Bean com.ondemandmonitoring.mission.service.IChecklistEvidenceService evidenceService(MissionRepository missions,
                com.ondemandmonitoring.mission.repository.MissionChecklistExecutionRepository executions,
                com.ondemandmonitoring.mission.repository.MissionChecklistEvidenceRepository evidence,
                com.ondemandmonitoring.media.repository.MediaAssetRepository media,
                com.ondemandmonitoring.mission.repository.MissionResultRepository results,
                com.ondemandmonitoring.mission.service.IMissionAuthorizationService auth,
                com.ondemandmonitoring.user.service.AuthenticatedUserResolver resolver,
                com.ondemandmonitoring.media.service.IMediaObjectStorage storage) {
            return new com.ondemandmonitoring.mission.service.impl.ChecklistEvidenceServiceImpl(missions, executions, evidence, media, results,
                    auth, resolver, new com.ondemandmonitoring.mission.service.impl.ChecklistEvidencePolicy(),
                    org.mapstruct.factory.Mappers.getMapper(com.ondemandmonitoring.mission.mapper.ChecklistEvidenceMapper.class), storage);
        }
    }
}
