package com.ondemandmonitoring.drone.domain;

import com.ondemandmonitoring.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "drone_models")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DroneModel extends BaseEntity {

    @Column(name = "model_code", unique = true, nullable = false, length = 100)
    private String modelCode;

    @Column(name = "manufacturer", nullable = false, length = 100)
    private String manufacturer;

    @Column(name = "category", nullable = false, length = 100)
    private String category;

    @Column(name = "max_flight_time_minutes")
    private Double maxFlightTimeMinutes;

    @Column(name = "max_takeoff_weight_kg")
    private Double maxTakeoffWeightKg;

    @Column(name = "max_flight_altitude_meters")
    private Double maxFlightAltitudeMeters;

    @Column(name = "max_wind_resistance_meters_per_second")
    private Double maxWindResistanceMetersPerSecond;

    @Column(name = "ip_rating", length = 50)
    private String ipRating;

    @Column(name = "specs_metadata", columnDefinition = "TEXT")
    private String specsMetadata;
}
