package com.ondemandmonitoring.order.mapper;

import com.ondemandmonitoring.order.domain.OrderChecklistItem;
import com.ondemandmonitoring.order.dto.response.OrderChecklistItemResponse;
import org.mapstruct.*;

@Mapper(componentModel = "spring", builder = @Builder(disableBuilder = true))
public interface OrderChecklistItemMapper {
    @Mapping(source = "sourceChecklist.id", target = "sourceChecklistId")
    OrderChecklistItemResponse toResponse(OrderChecklistItem item);
}
