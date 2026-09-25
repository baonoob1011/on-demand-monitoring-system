package com.ondemandmonitoring.order.repository;

import com.ondemandmonitoring.order.domain.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import com.ondemandmonitoring.order.enums.OrderStatus;

@Repository
public interface OrderRepository extends JpaRepository<Order, String> {
    List<Order> findByOrderStatusOrderByCreatedAtAsc(OrderStatus status);

    List<Order> findByCustomer_IdOrderByCreatedAtDesc(String customerId);

    List<Order> findByCustomer_IdAndOrderStatusOrderByCreatedAtDesc(String customerId, OrderStatus status);
}
