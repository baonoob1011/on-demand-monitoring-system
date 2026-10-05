package com.ondemandmonitoring.order.service;

import com.ondemandmonitoring.order.domain.Order;
import com.ondemandmonitoring.order.dto.request.OrderChecklistItemRequest;
import com.ondemandmonitoring.order.dto.response.OrderChecklistItemResponse;
import java.util.List;
import java.util.Map;

public interface IOrderChecklistSnapshotService {
    List<OrderChecklistItemResponse> capture(Order order, List<OrderChecklistItemRequest> items);
    Map<String, List<OrderChecklistItemResponse>> getByOrders(List<String> orderIds);
}
