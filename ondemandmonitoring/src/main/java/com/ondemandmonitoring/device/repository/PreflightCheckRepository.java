package com.ondemandmonitoring.device.repository;

import com.ondemandmonitoring.device.domain.PreflightCheck;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PreflightCheckRepository extends JpaRepository<PreflightCheck, String> {
}
