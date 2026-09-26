package com.ondemandmonitoring.missionv2.mapper;

import com.ondemandmonitoring.missionv2.domain.MissionV2;
import com.ondemandmonitoring.missionv2.dto.response.MissionV2Response;
import com.ondemandmonitoring.order.mapper.OrderMapper;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", uses = { OrderMapper.class })
public abstract class MissionMapper {

    @Mapping(source = "order.id", target = "orderId")
    public abstract MissionV2Response toResponse(MissionV2 mission);

}
