package com.ondemandmonitoring.zone.service.impl;

import com.ondemandmonitoring.common.exception.ApiException;
import com.ondemandmonitoring.common.exception.ErrorCode;
import com.ondemandmonitoring.zone.domain.ThermalSource;
import com.ondemandmonitoring.zone.domain.Zone;
import com.ondemandmonitoring.zone.config.SimulationZoneSeeder;
import com.ondemandmonitoring.zone.dto.request.ThermalSourceRequest;
import com.ondemandmonitoring.zone.dto.response.ThermalSourceResponse;
import com.ondemandmonitoring.zone.repository.ThermalSourceRepository;
import com.ondemandmonitoring.zone.repository.ZoneRepository;
import com.ondemandmonitoring.zone.service.IThermalSourceService;
import java.util.List;
import java.util.Locale;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ThermalSourceService implements IThermalSourceService {

    ThermalSourceRepository thermalSourceRepository;
    ZoneRepository zoneRepository;

    @Override
    @Transactional(readOnly = true)
    public List<ThermalSourceResponse> findAll() {
        return thermalSourceRepository.findAllByOrderByCodeAsc().stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ThermalSourceResponse> findByZoneCode(String zoneCode) {
        return thermalSourceRepository.findByZoneCodeOrderByCodeAsc(zoneCode).stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public ThermalSourceResponse create(ThermalSourceRequest request) {
        ThermalSource source = new ThermalSource();
        source.setCode(uniqueCode(request.getCode(), request.getName()));
        applyRequest(source, request);
        return toResponse(thermalSourceRepository.save(source));
    }

    @Override
    @Transactional
    public ThermalSourceResponse update(String id, ThermalSourceRequest request) {
        ThermalSource source = thermalSourceRepository.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Thermal source not found: " + id));
        applyRequest(source, request);
        return toResponse(thermalSourceRepository.save(source));
    }

    @Override
    @Transactional
    public void delete(String id) {
        ThermalSource source = thermalSourceRepository.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Thermal source not found: " + id));
        thermalSourceRepository.delete(source);
    }

    private ThermalSourceResponse toResponse(ThermalSource source) {
        return ThermalSourceResponse.builder()
                .id(source.getId())
                .code(source.getCode())
                .name(source.getName())
                .zoneCode(source.getZone().getCode())
                .thermalType(source.getThermalType())
                .temperatureC(source.getTemperatureC())
                .centerXM(source.getCenterXM())
                .centerYM(source.getCenterYM())
                .radiusM(source.getRadiusM())
                .active(source.isActive())
                .sourceWorld(source.getSourceWorld())
                .coordinateSystem(source.getCoordinateSystem())
                .build();
    }

    private void applyRequest(ThermalSource source, ThermalSourceRequest request) {
        Zone zone = zoneRepository.findByCode(request.getZoneCode())
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND,
                        "Zone not found: " + request.getZoneCode()));
        if (!isFinite(request.getCenterXM()) || !isFinite(request.getCenterYM())
                || !isFinite(request.getRadiusM()) || !isFinite(request.getTemperatureC())) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "Thermal source values must be finite numbers");
        }

        source.setZone(zone);
        source.setName(request.getName().trim());
        source.setThermalType(request.getThermalType());
        source.setTemperatureC(request.getTemperatureC());
        source.setCenterXM(request.getCenterXM());
        source.setCenterYM(request.getCenterYM());
        source.setRadiusM(request.getRadiusM());
        source.setActive(request.getActive() == null || request.getActive());
        source.setSourceWorld(SimulationZoneSeeder.SOURCE_WORLD);
        source.setCoordinateSystem(SimulationZoneSeeder.COORDINATE_SYSTEM);
    }

    private boolean isFinite(Double value) {
        return value != null && Double.isFinite(value);
    }

    private String uniqueCode(String requestedCode, String name) {
        String base = slug(requestedCode == null || requestedCode.isBlank() ? name : requestedCode);
        String candidate = base;
        int suffix = 2;
        while (thermalSourceRepository.existsByCode(candidate)) {
            candidate = base + "_" + suffix;
            suffix++;
        }
        return candidate;
    }

    private String slug(String value) {
        String normalized = value == null ? "THERMAL_SOURCE" : value.trim().toUpperCase(Locale.ROOT);
        normalized = normalized.replaceAll("[^A-Z0-9]+", "_").replaceAll("^_+|_+$", "");
        return normalized.isBlank() ? "THERMAL_SOURCE" : normalized;
    }
}
