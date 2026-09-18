package com.ondemandmonitoring.mission.repository;

import com.ondemandmonitoring.mission.domain.PostflightCheck;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PostflightCheckRepository extends JpaRepository<PostflightCheck, String> {

    List<PostflightCheck> findByMissionId(String missionId);

    Optional<PostflightCheck> findTopByMissionIdOrderByCheckedAtDesc(String missionId);
}
