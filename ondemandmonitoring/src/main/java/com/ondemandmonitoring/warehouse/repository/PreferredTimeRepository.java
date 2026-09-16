package com.ondemandmonitoring.warehouse.repository;

import com.ondemandmonitoring.warehouse.domain.PreferredTime;
import com.ondemandmonitoring.warehouse.enums.PreferredTimeCode;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PreferredTimeRepository extends JpaRepository<PreferredTime, String> {

    Optional<PreferredTime> findByCode(PreferredTimeCode code);

    boolean existsByCode(PreferredTimeCode code);
}
