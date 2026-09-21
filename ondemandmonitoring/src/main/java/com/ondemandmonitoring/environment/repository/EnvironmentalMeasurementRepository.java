package com.ondemandmonitoring.environment.repository;

import com.ondemandmonitoring.environment.domain.EnvironmentalMeasurement;
import com.ondemandmonitoring.environment.enums.MeasurementType;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EnvironmentalMeasurementRepository extends JpaRepository<EnvironmentalMeasurement, String> {

    List<EnvironmentalMeasurement> findTop20ByDroneCodeAndMeasurementTypeOrderByMeasuredAtDesc(
            String droneCode,
            MeasurementType measurementType);
}
