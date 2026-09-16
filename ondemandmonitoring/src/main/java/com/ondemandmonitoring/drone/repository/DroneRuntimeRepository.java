package com.ondemandmonitoring.drone.repository;

import com.ondemandmonitoring.drone.domain.DroneRuntime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DroneRuntimeRepository extends JpaRepository<DroneRuntime, String> {

    Optional<DroneRuntime> findByDroneCode(String droneCode);
}
