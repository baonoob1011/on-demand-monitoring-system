package com.ondemandmonitoring.order.service.impl;

import com.ondemandmonitoring.checklist.domain.ChecklistDefinition;
import com.ondemandmonitoring.checklist.repository.ChecklistDefinitionRepository;
import com.ondemandmonitoring.checklist.repository.ServiceChecklistRepository;
import com.ondemandmonitoring.checklist.util.ChecklistContentNormalizer;
import com.ondemandmonitoring.common.exception.ApiException;
import com.ondemandmonitoring.common.exception.ErrorCode;
import com.ondemandmonitoring.order.domain.Order;
import com.ondemandmonitoring.order.domain.OrderChecklistItem;
import com.ondemandmonitoring.order.dto.request.OrderChecklistItemRequest;
import com.ondemandmonitoring.order.dto.response.OrderChecklistItemResponse;
import com.ondemandmonitoring.order.enums.OrderChecklistSourceType;
import com.ondemandmonitoring.order.enums.ChecklistReviewStatus;
import com.ondemandmonitoring.order.enums.OrderStatus;
import com.ondemandmonitoring.order.mapper.OrderChecklistItemMapper;
import com.ondemandmonitoring.order.repository.OrderChecklistItemRepository;
import com.ondemandmonitoring.order.service.IOrderChecklistSnapshotService;
import com.ondemandmonitoring.service.repository.ServiceRepository;
import com.ondemandmonitoring.user.service.AuthenticatedUserResolver;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OrderChecklistSnapshotService implements IOrderChecklistSnapshotService {
    private final ServiceRepository services;
    private final ChecklistDefinitionRepository definitions;
    private final ServiceChecklistRepository assignments;
    private final OrderChecklistItemRepository snapshots;
    private final OrderChecklistItemMapper mapper;
    private final AuthenticatedUserResolver currentUser;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public List<OrderChecklistItemResponse> capture(Order order, List<OrderChecklistItemRequest> requested) {
        if (order.getChecklistSnapshotAt() != null || order.getOrderStatus() != OrderStatus.PENDING) {
            throw new ApiException(ErrorCode.ORDER_CHECKLIST_LOCKED);
        }
        if (order.getCustomer() == null
                || !Objects.equals(order.getCustomer().getId(), currentUser.getCurrentUser().getId())) {
            throw new ApiException(ErrorCode.ACCESS_DENIED);
        }
        var service = services.findByIdForUpdate(order.getService().getId())
                .orElseThrow(() -> new ApiException(ErrorCode.SERVICE_NOT_FOUND));
        if (!Boolean.TRUE.equals(service.getIsActive())) throw new ApiException(ErrorCode.SERVICE_INACTIVE);
        // Read IDs only: loading catalog entities before locking can leave stale
        // content/version in the persistence context after a concurrent edit.
        List<String> template = assignments.findTemplateChecklistIds(service.getId());
        Set<String> membership = new HashSet<>(template);
        List<OrderChecklistItemRequest> items = requested;
        boolean defaults = items == null;
        if (defaults) {
            items = template.stream().map(id -> {
                var item = new OrderChecklistItemRequest();
                item.setSourceChecklistId(id);
                return item;
            }).toList();
        }
        if (!defaults && items.size() > 100) throw invalid("At most 100 checklist items are allowed");
        Set<String> ids = new TreeSet<>();
        for (var item : items) {
            if (item == null) throw invalid("Checklist item cannot be null");
            String source = item.getSourceChecklistId();
            if (source != null) {
                if (source.isBlank() || !membership.contains(source))
                    throw new ApiException(ErrorCode.ORDER_CHECKLIST_TEMPLATE_CHANGED);
                if (!ids.add(source)) throw invalid("Duplicate source checklist");
                if (!defaults && (item.getExpectedChecklistVersion() == null || item.getExpectedChecklistVersion() < 0))
                    throw invalid("expectedChecklistVersion is required for a template item");
            } else if (item.getExpectedChecklistVersion() != null) throw invalid("Custom item cannot declare a catalog version");
        }
        Map<String, ChecklistDefinition> locked = new HashMap<>();
        for (String id : ids) locked.put(id, definitions.findByIdForUpdate(id)
                .orElseThrow(() -> new ApiException(ErrorCode.ORDER_CHECKLIST_TEMPLATE_CHANGED)));
        List<OrderChecklistItem> entities = new ArrayList<>();
        Set<String> contents = new HashSet<>();
        for (var item : items) {
            ChecklistDefinition source = locked.get(item.getSourceChecklistId());
            if (source != null && !Boolean.TRUE.equals(source.getIsActive())) {
                if (defaults) continue;
                throw new ApiException(ErrorCode.ORDER_CHECKLIST_TEMPLATE_CHANGED);
            }
            if (source != null && !defaults && !Objects.equals(source.getVersion(), item.getExpectedChecklistVersion()))
                throw new ApiException(ErrorCode.ORDER_CHECKLIST_TEMPLATE_CHANGED);
            var entity = new OrderChecklistItem();
            entity.setEvidencePolicyVersion(1);
            entity.setMinimumEvidenceCount(1);
            entity.setOrder(order);
            entity.setSourceChecklist(source);
            entity.setSourceType(source == null ? OrderChecklistSourceType.CUSTOMER_CUSTOM : OrderChecklistSourceType.SERVICE_TEMPLATE);
            entity.setReviewStatus(ChecklistReviewStatus.PENDING);
            String content = item.getContentOverride();
            if (content == null && source != null) {
                content = source.getContent();
            }
            entity.setContent(ChecklistContentNormalizer.content(content));
            if (!contents.add(entity.getContent().toLowerCase(Locale.ROOT))) throw invalid("Duplicate checklist content");
            entity.setDisplayOrder(entities.size());
            entities.add(entity);
        }
        if (entities.size() > 100) throw invalid("At most 100 checklist items are allowed");
        List<OrderChecklistItem> saved = snapshots.saveAllAndFlush(entities);
        order.setChecklistSnapshotAt(Instant.now());
        return saved.stream().map(mapper::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, List<OrderChecklistItemResponse>> getByOrders(List<String> orderIds) {
        if (orderIds.isEmpty()) return Map.of();
        return snapshots.findAllByOrderIdInOrderByDisplayOrderAscIdAsc(orderIds).stream()
                .collect(Collectors.groupingBy(item -> item.getOrder().getId(),
                        Collectors.mapping(mapper::toResponse, Collectors.toList())));
    }

    private ApiException invalid(String message) {
        return new ApiException(ErrorCode.INVALID_REQUEST, message);
    }
}
