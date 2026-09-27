package com.ondemandmonitoring.order.service;

import com.ondemandmonitoring.order.dto.request.OrderCreateRequest;
import com.ondemandmonitoring.order.dto.response.OrderCreateResponse;
import com.ondemandmonitoring.order.enums.OrderStatus;

public interface IOrderService {

    OrderCreateResponse createOrder(OrderCreateRequest request);

    OrderCreateResponse approveOrder(String orderId);

    void rejectOrder(String orderId, String reason);

    OrderCreateResponse getOrderById(String orderId);

    java.util.List<OrderCreateResponse> getPendingOrders();

    java.util.List<OrderCreateResponse> getMyOrders(OrderStatus status);
}
