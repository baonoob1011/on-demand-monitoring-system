package com.ondemandmonitoring.service.repository;

import com.ondemandmonitoring.service.domain.Service;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

@Repository
public interface ServiceRepository extends JpaRepository<Service, String> {

    boolean existsByNameIgnoreCase(String name);

    Optional<Service> findByNameIgnoreCase(String name);

    List<Service> findAllByIsActiveTrue();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Service s where s.id = :id")
    Optional<Service> findByIdForUpdate(@Param("id") String id);
}
