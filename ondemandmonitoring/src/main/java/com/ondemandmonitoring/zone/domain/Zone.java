package com.ondemandmonitoring.zone.domain;

import com.ondemandmonitoring.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.locationtech.jts.geom.Polygon;

@Entity
@Getter
@Setter
@Table(name = "zones")
public class Zone extends BaseEntity {

    @Column(name = "code", nullable = false, unique = true, length = 80)
    private String code;

    @Column(name = "name", nullable = false, unique = true, length = 120)
    private String name;

    @Column(name = "zone_type", nullable = false, length = 80)
    private String zoneType;

    @Column(name = "purpose", length = 500)
    private String purpose;

    @Column(name = "restricted", nullable = false)
    private boolean restricted = false;

    @Column(name = "center_x_m", nullable = false)
    private Double centerXM;

    @Column(name = "center_y_m", nullable = false)
    private Double centerYM;

    @Column(name = "radius_m", nullable = false)
    private Double radiusM;

    @Column(name = "source_world", nullable = false, length = 120)
    private String sourceWorld;

    @Column(name = "coordinate_system", nullable = false, length = 120)
    private String coordinateSystem;

    @Column(name = "polygon", nullable = false, columnDefinition = "geometry(Polygon,0)")
    private Polygon polygon;
}
