package com.ondemandmonitoring.media.service;

import com.ondemandmonitoring.common.api.PageResponse;
import com.ondemandmonitoring.media.dto.response.OperatorMissionMediaResponse;

public interface IManagerMediaApprovalService {

    PageResponse<OperatorMissionMediaResponse> listPending(String missionId, int page, int size);

    OperatorMissionMediaResponse approve(String missionId, String mediaId);

    OperatorMissionMediaResponse reject(String missionId, String mediaId);
}
