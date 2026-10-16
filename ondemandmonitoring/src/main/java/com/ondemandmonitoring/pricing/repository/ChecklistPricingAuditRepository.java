package com.ondemandmonitoring.pricing.repository;

import com.ondemandmonitoring.pricing.domain.ChecklistPricingAudit;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChecklistPricingAuditRepository extends JpaRepository<ChecklistPricingAudit, String> {
}
