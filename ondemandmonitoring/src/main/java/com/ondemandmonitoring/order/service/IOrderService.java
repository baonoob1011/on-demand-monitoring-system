package com.ondemandmonitoring.order.service;

import com.ondemandmonitoring.order.dto.request.OrderCreateRequest;
import com.ondemandmonitoring.order.dto.response.OrderCreateResponse;

public interface IOrderService {

    OrderCreateResponse createOrder(OrderCreateRequest request);
}
