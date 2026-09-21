package com.ondemandmonitoring.service.repository;

import com.ondemandmonitoring.service.domain.DeliverableType;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DeliverableTypeRepository extends JpaRepository<DeliverableType, String> {

    boolean existsByNameIgnoreCase(String name);

    List<DeliverableType> findAllByIsActiveTrue();
}
