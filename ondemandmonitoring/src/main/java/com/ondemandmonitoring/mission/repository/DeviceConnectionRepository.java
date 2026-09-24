package com.ondemandmonitoring.mission.repository;

import com.ondemandmonitoring.mission.domain.DeviceConnection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DeviceConnectionRepository extends JpaRepository<DeviceConnection, String> {

    List<DeviceConnection> findByMissionId(String missionId);

    Optional<DeviceConnection> findTopByMissionIdAndConnectionStatusOrderByConnectedAtDesc(String missionId, String connectionStatus);
}
