package com.ondemandmonitoring.mission.repository;

import com.ondemandmonitoring.mission.domain.MissionRescheduleHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MissionRescheduleHistoryRepository extends JpaRepository<MissionRescheduleHistory, String> {
    List<MissionRescheduleHistory> findByMissionIdOrderByRescheduledAtDesc(String missionId);
}
