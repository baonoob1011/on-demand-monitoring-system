package com.ondemandmonitoring.device.domain;

import com.ondemandmonitoring.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "device_models")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DeviceModel extends BaseEntity {

    @Column(name = "code", nullable = false, unique = true, length = 50)
    private String code;

    @Column(name = "manufacturer", length = 100)
    private String manufacturer;

    @Column(name = "model_name", nullable = false, length = 100)
    private String modelName;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "specs_metadata", columnDefinition = "jsonb")
    private Map<String, Object> specsMetadata;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "device_model_types", joinColumns = @JoinColumn(name = "device_model_id"), inverseJoinColumns = @JoinColumn(name = "device_type_id"))
    private Set<DeviceType> deviceTypes = new HashSet<>();
}
