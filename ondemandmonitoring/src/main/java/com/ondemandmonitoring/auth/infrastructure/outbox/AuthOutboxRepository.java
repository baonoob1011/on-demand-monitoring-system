package com.ondemandmonitoring.auth.infrastructure.outbox;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface AuthOutboxRepository extends JpaRepository<AuthOutboxEvent, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select e from AuthOutboxEvent e
            where (e.status = :status and e.nextAttemptAt <= :now)
               or (e.status = com.ondemandmonitoring.auth.infrastructure.outbox.AuthOutboxStatus.PROCESSING
                   and e.claimedAt <= :staleBefore)
            order by e.createdAt asc
            """)
    List<AuthOutboxEvent> findNextBatch(@Param("status") AuthOutboxStatus status,
                                        @Param("now") Instant now,
                                        @Param("staleBefore") Instant staleBefore,
                                        Pageable pageable);
}
