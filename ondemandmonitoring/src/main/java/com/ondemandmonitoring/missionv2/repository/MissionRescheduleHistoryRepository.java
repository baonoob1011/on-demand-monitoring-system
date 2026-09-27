package com.ondemandmonitoring.missionv2.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ondemandmonitoring.missionv2.domain.MissionRescheduleHistory;

import java.util.List;

public interface MissionRescheduleHistoryRepository extends JpaRepository<MissionRescheduleHistory, String> {
    List<MissionRescheduleHistory> findByMissionIdOrderByRescheduledAtDesc(String missionId);
}
