package com.ondemandmonitoring.order.service;

import com.ondemandmonitoring.order.dto.request.OrderCreateRequest;
import com.ondemandmonitoring.order.dto.response.OrderCreateResponse;
import com.ondemandmonitoring.mission.dto.response.MissionResponse;

public interface IOrderService {

    OrderCreateResponse createOrder(OrderCreateRequest request);

    MissionResponse approveOrder(String orderId);

    void rejectOrder(String orderId, String reason);

    OrderCreateResponse getOrderById(String orderId);

    java.util.List<OrderCreateResponse> getPendingOrders();
}

