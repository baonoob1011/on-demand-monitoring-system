package com.ondemandmonitoring.order.repository;

import com.ondemandmonitoring.order.domain.OrderChecklistItem;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;

public interface OrderChecklistItemRepository extends JpaRepository<OrderChecklistItem, String> {
    List<OrderChecklistItem> findAllByOrderIdInOrderByDisplayOrderAscIdAsc(Collection<String> orderIds);

    List<OrderChecklistItem> findAllByOrderIdOrderByDisplayOrderAscIdAsc(String orderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<OrderChecklistItem> findByIdAndOrderId(String id, String orderId);
}
