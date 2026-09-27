package com.ondemandmonitoring.missionv2.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.ondemandmonitoring.missionv2.domain.MissionDeviceAssignment;

@Repository
public interface MissionDeviceAssignmentRepository extends JpaRepository<MissionDeviceAssignment, String> {
}
