package com.ondemandmonitoring.mission.repository;

import com.ondemandmonitoring.device.domain.PostflightCheck;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PostflightCheckRepository extends JpaRepository<PostflightCheck, String> {

    @EntityGraph(attributePaths = {"items", "mission", "deviceConnection", "deviceConnection.device"})
    List<PostflightCheck> findByMissionId(String missionId);

    @EntityGraph(attributePaths = {"items", "mission", "deviceConnection", "deviceConnection.device"})
    Optional<PostflightCheck> findTopByMissionIdOrderByCheckedAtDesc(String missionId);
}
