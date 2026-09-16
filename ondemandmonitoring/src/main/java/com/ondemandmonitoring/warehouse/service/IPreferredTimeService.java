package com.ondemandmonitoring.warehouse.service;

import com.ondemandmonitoring.warehouse.dto.request.PreferredTimeCreateRequest;
import com.ondemandmonitoring.warehouse.dto.request.PreferredTimeUpdateRequest;
import com.ondemandmonitoring.warehouse.dto.response.PreferredTimeResponse;
import com.ondemandmonitoring.warehouse.enums.PreferredTimeCode;
import java.util.List;

public interface IPreferredTimeService {

    PreferredTimeResponse create(PreferredTimeCreateRequest request);

    PreferredTimeResponse getById(String id);

    PreferredTimeResponse getByCode(PreferredTimeCode code);

    List<PreferredTimeResponse> getAll();

    PreferredTimeResponse update(String id, PreferredTimeUpdateRequest request);

    void delete(String id);
}
