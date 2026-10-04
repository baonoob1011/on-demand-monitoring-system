package com.ondemandmonitoring.order.repository;

import com.ondemandmonitoring.order.domain.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.ondemandmonitoring.order.enums.OrderStatus;

@Repository
public interface OrderRepository extends JpaRepository<Order, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM Order o WHERE o.id = :id")
    Optional<Order> findByIdForUpdate(@Param("id") String id);

    boolean existsByOrderCode(String orderCode);

    List<Order> findByOrderStatusOrderByCreatedAtAsc(OrderStatus status);

    List<Order> findByCustomer_IdOrderByCreatedAtDesc(String customerId);

    List<Order> findByCustomer_IdAndOrderStatusOrderByCreatedAtDesc(String customerId, OrderStatus status);
}
