package com.ondemandmonitoring.mission.mapper;

import com.ondemandmonitoring.mission.domain.Mission;
import com.ondemandmonitoring.mission.dto.response.CustomerMissionHistoryResponse;
import org.mapstruct.Builder;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", builder = @Builder(disableBuilder = true))
public interface CustomerMissionHistoryMapper {
    @Mapping(target = "orderId", source = "order.id")
    @Mapping(target = "orderTitle", source = "order.title")
    @Mapping(target = "address", source = "order.address")
    CustomerMissionHistoryResponse toResponse(Mission mission);
}
