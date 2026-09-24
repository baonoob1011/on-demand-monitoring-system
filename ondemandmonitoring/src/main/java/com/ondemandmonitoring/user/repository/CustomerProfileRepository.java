package com.ondemandmonitoring.user.repository;

import com.ondemandmonitoring.user.domain.CustomerProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CustomerProfileRepository extends JpaRepository<CustomerProfile, String> {
}
