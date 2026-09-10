package com.ondemandmonitoring.zone.service;

import com.ondemandmonitoring.zone.dto.request.ZoneCreateRequest;
import com.ondemandmonitoring.zone.dto.request.ZonePolygonUpdateRequest;
import com.ondemandmonitoring.zone.dto.response.ZoneResponse;
import java.util.List;

public interface IZoneService {

    List<ZoneResponse> findAll();

    ZoneResponse create(ZoneCreateRequest request);

    ZoneResponse updatePolygon(String id, ZonePolygonUpdateRequest request);

    void delete(String id);
}
