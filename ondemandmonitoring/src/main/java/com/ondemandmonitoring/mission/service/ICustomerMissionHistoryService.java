package com.ondemandmonitoring.mission.service;

import com.ondemandmonitoring.common.api.PageResponse;
import com.ondemandmonitoring.mission.dto.response.CustomerMissionHistoryResponse;

public interface ICustomerMissionHistoryService {
    PageResponse<CustomerMissionHistoryResponse> listHistory(int page, int size);
    CustomerMissionHistoryResponse getHistory(String missionId);
}
