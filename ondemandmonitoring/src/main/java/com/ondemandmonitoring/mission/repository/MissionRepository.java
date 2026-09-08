package com.ondemandmonitoring.mission.repository;

import com.ondemandmonitoring.mission.domain.Mission;
import com.ondemandmonitoring.mission.enums.MissionStatus;
import java.util.Collection;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MissionRepository extends JpaRepository<Mission, String> {

    boolean existsByMissionCode(String missionCode);

    Optional<Mission> findByMissionCode(String missionCode);

    Optional<Mission> findFirstByAssignedDeviceCodeAndStatusInOrderByCreatedAtAsc(
            String assignedDeviceCode,
            Collection<MissionStatus> statuses);
}
