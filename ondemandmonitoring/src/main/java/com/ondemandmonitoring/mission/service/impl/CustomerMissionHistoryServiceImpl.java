package com.ondemandmonitoring.mission.service.impl;

import com.ondemandmonitoring.common.api.PageResponse;
import com.ondemandmonitoring.common.exception.ApiException;
import com.ondemandmonitoring.common.exception.ErrorCode;
import com.ondemandmonitoring.mission.dto.response.CustomerMissionHistoryResponse;
import com.ondemandmonitoring.mission.enums.MissionStatus;
import com.ondemandmonitoring.mission.mapper.CustomerMissionHistoryMapper;
import com.ondemandmonitoring.mission.repository.MissionRepository;
import com.ondemandmonitoring.mission.service.ICustomerMissionHistoryService;
import com.ondemandmonitoring.user.service.AuthenticatedUserResolver;
import java.util.List;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class CustomerMissionHistoryServiceImpl implements ICustomerMissionHistoryService {
    static final List<MissionStatus> HISTORY_STATUSES =
            List.of(MissionStatus.COMPLETED, MissionStatus.FAILED, MissionStatus.CANCELLED);
    MissionRepository missions;
    AuthenticatedUserResolver currentUser;
    CustomerMissionHistoryMapper mapper;

    @Override
    public PageResponse<CustomerMissionHistoryResponse> listHistory(int page, int size) {
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "updatedAt", "id"));
        return PageResponse.from(missions.findByOrder_Customer_IdAndStatusIn(
                currentUser.getCurrentUserId(), HISTORY_STATUSES, pageable).map(mapper::toResponse));
    }

    @Override
    public CustomerMissionHistoryResponse getHistory(String missionId) {
        return missions.findByIdAndOrder_Customer_Id(missionId, currentUser.getCurrentUserId())
                .filter(mission -> HISTORY_STATUSES.contains(mission.getStatus()))
                .map(mapper::toResponse)
                .orElseThrow(() -> new ApiException(ErrorCode.MISSION_NOT_FOUND));
    }
}
