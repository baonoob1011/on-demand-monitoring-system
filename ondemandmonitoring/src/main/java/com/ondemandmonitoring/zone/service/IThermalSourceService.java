package com.ondemandmonitoring.zone.service;

import com.ondemandmonitoring.zone.dto.response.ThermalSourceResponse;
import com.ondemandmonitoring.zone.dto.request.ThermalSourceRequest;
import java.util.List;

public interface IThermalSourceService {

    List<ThermalSourceResponse> findAll();

    List<ThermalSourceResponse> findByZoneCode(String zoneCode);

    ThermalSourceResponse create(ThermalSourceRequest request);

    ThermalSourceResponse update(String id, ThermalSourceRequest request);

    void delete(String id);
}
