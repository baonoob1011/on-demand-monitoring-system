package com.ondemandmonitoring.order.service.impl;

import com.ondemandmonitoring.categoryservice.domain.CategoryService;
import com.ondemandmonitoring.categoryservice.repository.CategoryServiceRepository;
import com.ondemandmonitoring.common.exception.ApiException;
import com.ondemandmonitoring.common.exception.ErrorCode;
import com.ondemandmonitoring.order.domain.Order;
import com.ondemandmonitoring.order.dto.request.OrderCreateRequest;
import com.ondemandmonitoring.order.dto.response.OrderCreateResponse;
import com.ondemandmonitoring.order.enums.OrderStatus;
import com.ondemandmonitoring.order.mapper.OrderMapper;
import com.ondemandmonitoring.order.repository.OrderRepository;
import com.ondemandmonitoring.order.service.IOrderService;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class OrderService implements IOrderService {

    OrderRepository orderRepository;
    CategoryServiceRepository categoryServiceRepository;
    OrderMapper orderMapper;

    @Override
    @Transactional
    public OrderCreateResponse createOrder(OrderCreateRequest request) {

        // TODO check Customer

        // Validate & fetch Category Service entity
        CategoryService categoryService = categoryServiceRepository.findById(request.getCategoryServiceId())
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND,
                        "Category service not found with id: " + request.getCategoryServiceId()));

        Order order = orderMapper.toEntity(request);
        order.setCategoryService(categoryService);
        order.setOrderStatus(OrderStatus.PENDING);

        LocalDateTime now = LocalDateTime.now();
        order.setCreatedAt(now);
        order.setUpdatedAt(now);

        Order savedOrder = orderRepository.save(order);
        return orderMapper.toResponse(savedOrder);
    }
}
