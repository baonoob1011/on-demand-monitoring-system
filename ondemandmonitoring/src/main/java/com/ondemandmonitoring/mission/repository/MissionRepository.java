package com.ondemandmonitoring.mission.repository;

import com.ondemandmonitoring.mission.domain.Mission;
import com.ondemandmonitoring.mission.enums.MissionStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MissionRepository extends JpaRepository<Mission, String>, JpaSpecificationExecutor<Mission> {
    @Override
    Optional<Mission> findById(String id);


    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT m FROM Mission m WHERE m.id = :id")
    Optional<Mission> findByIdForUpdate(@Param("id") String id);

    @EntityGraph(attributePaths = "order")
    @Query("SELECT m FROM Mission m WHERE m.id = :id")
    Optional<Mission> findByIdWithOrder(@Param("id") String id);

    Optional<Mission> findByMissionCode(String missionCode);

    Optional<Mission> findByOrderId(String orderId);

    boolean existsByMissionCode(String missionCode);

    boolean existsByOrderId(String orderId);

    List<Mission> findByOrder_Customer_Id(String customerId);

    @EntityGraph(attributePaths = "order")
    Page<Mission> findByOrder_Customer_IdAndStatusIn(
            String customerId, List<MissionStatus> statuses, Pageable pageable);

    @EntityGraph(attributePaths = "order")
    Optional<Mission> findByIdAndOrder_Customer_Id(String id, String customerId);

    @Query("""
            SELECT DISTINCT m FROM Mission m
            JOIN MissionStaffAssignment msa ON msa.mission = m
            WHERE msa.staff.id = :staffId
              AND m.status IN :statuses
            """)
    List<Mission> findByStaffIdAndStatusIn(@Param("staffId") String staffId, @Param("statuses") List<MissionStatus> statuses);

    List<Mission> findByStatusIn(List<MissionStatus> statuses);

    /**
     * Find missions assigned to a device whose scheduled window overlaps [startAt, endAt].
     * Used to validate device replacement availability / schedule conflict.
     */
    @Query("""
            SELECT m FROM Mission m
            JOIN MissionDeviceAssignment mda ON mda.mission = m
            WHERE mda.device.id = :deviceId
              AND mda.isCurrent = true
              AND m.status NOT IN ('COMPLETED', 'FAILED', 'CANCELLED')
              AND m.completedAt IS NULL
            """)
    List<Mission> findActiveByDeviceId(@Param("deviceId") String deviceId);
}


