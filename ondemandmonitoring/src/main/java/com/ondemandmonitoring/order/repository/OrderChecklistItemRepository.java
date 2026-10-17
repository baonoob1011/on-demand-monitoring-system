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

    /** How many order lines were taken from each library checklist (grouped, one query per page). */
    @org.springframework.data.jpa.repository.Query("select i.sourceChecklist.id as checklistId, count(i) as total "
            + "from OrderChecklistItem i where i.sourceChecklist.id in :ids group by i.sourceChecklist.id")
    List<UsageCount> countBySourceChecklistIds(
            @org.springframework.data.repository.query.Param("ids") Collection<String> ids);

    interface UsageCount {
        String getChecklistId();
        long getTotal();
    }

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<OrderChecklistItem> findByIdAndOrderId(String id, String orderId);
}
