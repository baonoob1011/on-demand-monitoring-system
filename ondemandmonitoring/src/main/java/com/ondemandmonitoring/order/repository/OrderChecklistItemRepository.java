package com.ondemandmonitoring.order.repository;

import com.ondemandmonitoring.order.domain.OrderChecklistItem;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Collection;
import java.util.List;

public interface OrderChecklistItemRepository extends JpaRepository<OrderChecklistItem, String> {
    List<OrderChecklistItem> findAllByOrderIdInOrderByDisplayOrderAscIdAsc(Collection<String> orderIds);
}
