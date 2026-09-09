package com.ondemandmonitoring.zone.domain;

import com.ondemandmonitoring.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.locationtech.jts.geom.Geometry;

@Entity
@Getter
@Setter
@Table(name = "simulation_map_features")
public class SimulationMapFeature extends BaseEntity {

    @Column(name = "code", nullable = false, unique = true, length = 80)
    private String code;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "feature_type", nullable = false, length = 80)
    private String featureType;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder;

    @Column(name = "source_world", nullable = false, length = 120)
    private String sourceWorld;

    @Column(name = "coordinate_system", nullable = false, length = 120)
    private String coordinateSystem;

    @Column(name = "geometry", nullable = false, columnDefinition = "geometry(Geometry,0)")
    private Geometry geometry;
}
