package com.ondemandmonitoring.mission.repository;

import com.ondemandmonitoring.mission.domain.GcsSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GcsSessionRepository extends JpaRepository<GcsSession, String> {

    List<GcsSession> findByMissionId(String missionId);

    Optional<GcsSession> findTopByMissionIdAndConnectionStatusOrderByConnectedAtDesc(String missionId, String connectionStatus);
}
