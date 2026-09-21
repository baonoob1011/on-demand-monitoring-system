package com.ondemandmonitoring.service.repository;

import com.ondemandmonitoring.service.domain.Service;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ServiceRepository extends JpaRepository<Service, String> {

    boolean existsByNameIgnoreCase(String name);

    List<Service> findAllByIsActiveTrue();
}
