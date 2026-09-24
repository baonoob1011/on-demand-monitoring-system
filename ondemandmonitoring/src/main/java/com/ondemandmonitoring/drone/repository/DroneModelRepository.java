package com.ondemandmonitoring.drone.repository;

import com.ondemandmonitoring.drone.domain.DroneModel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DroneModelRepository extends JpaRepository<DroneModel, String> {

    Optional<DroneModel> findByModelCode(String modelCode);

    @Query("SELECT d FROM DroneModel d WHERE " +
           "(:category IS NULL OR LOWER(d.category) LIKE LOWER(CONCAT('%', :category, '%'))) AND " +
           "(:manufacturer IS NULL OR LOWER(d.manufacturer) LIKE LOWER(CONCAT('%', :manufacturer, '%')))")
    Page<DroneModel> searchDroneModels(
            @Param("category") String category,
            @Param("manufacturer") String manufacturer,
            Pageable pageable
    );
}
