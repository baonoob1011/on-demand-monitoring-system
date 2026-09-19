package com.ondemandmonitoring.zone.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ondemandmonitoring.zone.domain.ThermalSource;
import com.ondemandmonitoring.zone.enums.ThermalType;
import com.ondemandmonitoring.zone.domain.Zone;
import com.ondemandmonitoring.zone.dto.response.ThermalSourceResponse;
import com.ondemandmonitoring.zone.repository.ThermalSourceRepository;
import com.ondemandmonitoring.zone.repository.ZoneRepository;
import com.ondemandmonitoring.zone.service.impl.ThermalSourceService;
import java.util.List;
import org.junit.jupiter.api.Test;

class ThermalSourceServiceTest {

    @Test
    void returnsValuesReadFromRepository() {
        ThermalSourceRepository repository = mock(ThermalSourceRepository.class);
        ThermalSource source = thermalSource();
        when(repository.findAllByOrderByCodeAsc()).thenReturn(List.of(source));
        when(repository.findByZoneCodeOrderByCodeAsc("FOREST_MONITORING_AREA"))
                .thenReturn(List.of(source));
        ThermalSourceService service = new ThermalSourceService(repository, mock(ZoneRepository.class));

        List<ThermalSourceResponse> all = service.findAll();
        List<ThermalSourceResponse> byZone = service.findByZoneCode("FOREST_MONITORING_AREA");

        assertThat(all).hasSize(1);
        assertThat(byZone).hasSize(1);
        ThermalSourceResponse response = all.getFirst();
        assertThat(response.getCode()).isEqualTo("DB_THERMAL_SOURCE");
        assertThat(response.getZoneCode()).isEqualTo("FOREST_MONITORING_AREA");
        assertThat(response.getThermalType()).isEqualTo(ThermalType.FIRE);
        assertThat(response.getTemperatureC()).isEqualTo(237.5);
        assertThat(response.getCenterXM()).isEqualTo(-159.81);
        assertThat(response.getCenterYM()).isEqualTo(154.61);
        assertThat(response.getRadiusM()).isEqualTo(2.0);
        assertThat(response.getActive()).isTrue();
        assertThat(response.getCoordinateSystem()).isEqualTo("LOCAL_SIMULATION_METERS_GAZEBO_XY");
    }

    private ThermalSource thermalSource() {
        Zone zone = new Zone();
        zone.setCode("FOREST_MONITORING_AREA");

        ThermalSource source = new ThermalSource();
        source.setZone(zone);
        source.setCode("DB_THERMAL_SOURCE");
        source.setName("Database Thermal Source");
        source.setThermalType(ThermalType.FIRE);
        source.setTemperatureC(237.5);
        source.setCenterXM(-159.81);
        source.setCenterYM(154.61);
        source.setRadiusM(2.0);
        source.setActive(true);
        source.setSourceWorld("forest_monitoring_compact");
        source.setCoordinateSystem("LOCAL_SIMULATION_METERS_GAZEBO_XY");
        return source;
    }
}
