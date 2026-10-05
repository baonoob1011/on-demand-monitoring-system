package com.ondemandmonitoring.mission.repository;

import com.ondemandmonitoring.mission.domain.MissionResult;
import com.ondemandmonitoring.mission.enums.MissionResultApprovalStatus;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MissionResultRepository extends JpaRepository<MissionResult, String> {
    @Query("select r.mission.id from MissionResult r where r.id = :resultId")
    Optional<String> findMissionIdByResultId(@Param("resultId") String resultId);

    @Query("select r from MissionResult r where r.mission.id = :missionId")
    Optional<MissionResult> findByMissionId(@Param("missionId") String missionId);

    boolean existsByMissionId(String missionId);

    boolean existsByMission_Order_IdAndApprovalStatus(String orderId, MissionResultApprovalStatus approvalStatus);

    Page<MissionResult> findByApprovalStatus(MissionResultApprovalStatus approvalStatus, Pageable pageable);
}
