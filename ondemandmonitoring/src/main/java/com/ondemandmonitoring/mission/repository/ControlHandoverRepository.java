package com.ondemandmonitoring.mission.repository;

import com.ondemandmonitoring.mission.domain.ControlHandover;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ControlHandoverRepository extends JpaRepository<ControlHandover, String> {

    List<ControlHandover> findByDroneId(String droneId);

    List<ControlHandover> findByOperatorId(String operatorId);
}
