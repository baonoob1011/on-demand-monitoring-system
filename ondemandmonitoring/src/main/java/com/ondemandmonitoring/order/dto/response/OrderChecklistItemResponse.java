package com.ondemandmonitoring.order.dto.response;

import com.ondemandmonitoring.order.enums.OrderChecklistSourceType;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

@Getter
@Setter
@NoArgsConstructor
public class OrderChecklistItemResponse {
    private String id;
    private String sourceChecklistId;
    private String content;
    private int displayOrder;
    private OrderChecklistSourceType sourceType;
}
