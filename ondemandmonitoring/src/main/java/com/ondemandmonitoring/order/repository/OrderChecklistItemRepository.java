package com.ondemandmonitoring.order.repository;

import com.ondemandmonitoring.order.domain.OrderChecklistItem;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import com.ondemandmonitoring.order.enums.ChecklistPricingStatus;
import com.ondemandmonitoring.order.enums.OrderChecklistSourceType;

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

    long countByPricingStatusAndSourceTypeIn(
            ChecklistPricingStatus pricingStatus, Collection<OrderChecklistSourceType> sourceTypes);

    long countByPricingStatus(ChecklistPricingStatus pricingStatus);

    @EntityGraph(attributePaths = {"order", "order.customer", "order.service"})
    List<OrderChecklistItem> findByPricingStatusAndSourceTypeInOrderByCreatedAtAsc(
            ChecklistPricingStatus pricingStatus, Collection<OrderChecklistSourceType> sourceTypes, Pageable pageable);

    @EntityGraph(attributePaths = {"order", "order.customer", "order.service"})
    List<OrderChecklistItem> findByPricingStatusOrderByCreatedAtAsc(
            ChecklistPricingStatus pricingStatus, Pageable pageable);

    @org.springframework.data.jpa.repository.Query("""
            select i.order.id as orderId,
                   count(i) as additionalCount,
                   sum(case when i.pricingStatus = com.ondemandmonitoring.order.enums.ChecklistPricingStatus.NEEDS_MANAGER_REVIEW then 1 else 0 end) as pendingCount
            from OrderChecklistItem i
            where i.order.id in :orderIds and i.sourceType in :sourceTypes
            group by i.order.id
            """)
    List<OrderAdditionalChecklistCount> countAdditionalByOrderIds(
            @org.springframework.data.repository.query.Param("orderIds") Collection<String> orderIds,
            @org.springframework.data.repository.query.Param("sourceTypes") Collection<OrderChecklistSourceType> sourceTypes);

    interface OrderAdditionalChecklistCount {
        String getOrderId();
        long getAdditionalCount();
        long getPendingCount();
    }
}
