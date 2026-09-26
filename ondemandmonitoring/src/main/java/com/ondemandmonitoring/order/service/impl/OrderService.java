package com.ondemandmonitoring.order.service.impl;

import com.ondemandmonitoring.common.exception.ApiException;
import com.ondemandmonitoring.common.exception.ErrorCode;
import com.ondemandmonitoring.mission.service.IMissionService;
import com.ondemandmonitoring.mission.dto.response.MissionResponse;
import com.ondemandmonitoring.order.domain.Order;
import com.ondemandmonitoring.order.domain.OrderDeliverable;
import com.ondemandmonitoring.order.dto.request.OrderCreateRequest;
import com.ondemandmonitoring.order.dto.request.OrderDeliverableRequest;
import com.ondemandmonitoring.order.dto.response.OrderCreateResponse;
import com.ondemandmonitoring.order.enums.OrderStatus;
import com.ondemandmonitoring.order.mapper.OrderMapper;
import com.ondemandmonitoring.order.repository.OrderRepository;
import com.ondemandmonitoring.order.service.IOrderService;
import com.ondemandmonitoring.order.util.GeoReader;
import com.ondemandmonitoring.service.domain.DeliverableType;
import com.ondemandmonitoring.service.domain.Service;
import com.ondemandmonitoring.service.repository.DeliverableTypeRepository;
import com.ondemandmonitoring.service.repository.ServiceDeliverableRepository;
import com.ondemandmonitoring.service.repository.ServiceRepository;
import com.ondemandmonitoring.user.domain.User;
import com.ondemandmonitoring.user.service.AuthenticatedUserResolver;
import com.ondemandmonitoring.warehouse.domain.PreferredTime;
import com.ondemandmonitoring.warehouse.repository.PreferredTimeRepository;
import com.ondemandmonitoring.zone.domain.Zone;
import com.ondemandmonitoring.zone.repository.ZoneRepository;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class OrderService implements IOrderService {

    OrderRepository orderRepository;
    ServiceRepository serviceRepository;
    DeliverableTypeRepository deliverableTypeRepository;
    ServiceDeliverableRepository serviceDeliverableRepository;
    PreferredTimeRepository preferredTimeRepository;
    ZoneRepository zoneRepository;
    AuthenticatedUserResolver authenticatedUserResolver;
    IMissionService missionService;
    OrderMapper orderMapper;

    @Override
    @Transactional
    public OrderCreateResponse approveOrder(String orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Order not found: " + orderId));

        if (order.getOrderStatus() != OrderStatus.PENDING) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "Only PENDING orders can be approved");
        }

        order.setOrderStatus(OrderStatus.APPROVED);
        orderRepository.save(order);

        // Flow 2: Create mission for the approved order
        return orderMapper.toResponse(order);
    }

    @Override
    @Transactional
    public OrderCreateResponse createOrder(OrderCreateRequest request) {

        // 1. Resolve Customer from logged-in user session
        User customer = authenticatedUserResolver.getCurrentUser();

        // 2. Validate & fetch Service
        Service service = serviceRepository.findById(request.getServiceId())
                .orElseThrow(() -> new ApiException(ErrorCode.SERVICE_NOT_FOUND,
                        "Service not found with id: " + request.getServiceId()));

        // 3. Validate & fetch Preferred Time
        PreferredTime preferredTime = preferredTimeRepository.findById(request.getPreferredTimeId())
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND,
                        "Preferred time not found with id: " + request.getPreferredTimeId()));

        // 4. Validate Date Range
        if (request.getPreferredDateFrom().isAfter(request.getPreferredDateTo())) {
            throw new ApiException(ErrorCode.INVALID_REQUEST,
                    "preferredDateFrom must be before or equal to preferredDateTo");
        }

        // 5. Convert & validate GeoJSON geometries
        Point location = GeoReader.createPoint(request.getLongitude(), request.getLatitude());
        if (location == null) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "Invalid location coordinates");
        }
        Polygon targetArea = GeoReader.readPolygonFromCoverageArea(request.getCoverageArea());
        if (targetArea == null) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "Invalid coverageArea GeoJSON");
        }

        validatePointInsideZone(location);

        // 6. Validate Deliverables requirement
        if (request.getDeliverables() == null || request.getDeliverables().isEmpty()) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "At least one deliverable is required for this service");
        }

        List<OrderDeliverable> orderDeliverables = new ArrayList<>();
        for (OrderDeliverableRequest delReq : request.getDeliverables()) {
            DeliverableType delType = deliverableTypeRepository.findById(delReq.getDeliverableTypeId())
                    .orElseThrow(() -> new ApiException(ErrorCode.DELIVERABLE_TYPE_NOT_FOUND,
                            "Deliverable type not found with id: " + delReq.getDeliverableTypeId()));

            boolean isAssociated = serviceDeliverableRepository.existsByServiceIdAndDeliverableTypeId(
                    service.getId(), delType.getId());
            if (!isAssociated) {
                throw new ApiException(ErrorCode.INVALID_REQUEST,
                        String.format("Deliverable type '%s' is not associated with service '%s'",
                                delType.getName(), service.getName()));
            }

            OrderDeliverable orderDeliverable = OrderDeliverable.builder()
                    .deliverableType(delType)
                    .requirement(delReq.getRequirement())
                    .build();

            orderDeliverables.add(orderDeliverable);
        }

        // 7. Map and persist order
        Order order = orderMapper.toEntity(request);
        order.setCustomer(customer);
        order.setService(service);
        order.setPreferredTime(preferredTime);
        order.setPoint(location);
        order.setTargetArea(targetArea);
        order.setOrderStatus(OrderStatus.PENDING);
        order.setReviewBy(null);
        order.setReviewAt(null);

        for (OrderDeliverable od : orderDeliverables) {
            od.setOrder(order);
        }
        order.setDeliverables(orderDeliverables);

        Order savedOrder = orderRepository.save(order);
        return orderMapper.toResponse(savedOrder);
    }

    private void validatePointInsideZone(Point point) {
        List<Zone> zones = zoneRepository.findAll();
        if (zones.isEmpty()) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "No monitoring zones configured in the system");
        }

        boolean contains = false;
        for (Zone zone : zones) {
            if (zone.getPolygon() != null && (zone.getPolygon().contains(point) || zone.getPolygon().covers(point))) {
                contains = true;
                break;
            }
        }

        if (!contains) {
            throw new ApiException(ErrorCode.INVALID_REQUEST,
                    String.format("Location point [%f, %f] is not within any defined monitoring zone",
                            point.getX(), point.getY()));
        }
    }

    @Override
    @Transactional(readOnly = true)
    public OrderCreateResponse getOrderById(String orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Order not found: " + orderId));
        return orderMapper.toResponse(order);
    }

    @Override
    @Transactional
    public void rejectOrder(String orderId, String reason) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Order not found: " + orderId));

        if (order.getOrderStatus() != OrderStatus.PENDING) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "Only PENDING orders can be rejected");
        }

        order.setOrderStatus(OrderStatus.REJECTED);
        order.setRejectReason(reason);
        order.setReviewAt(java.time.Instant.now());
        orderRepository.save(order);
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderCreateResponse> getPendingOrders() {
        return orderRepository.findByOrderStatusOrderByCreatedAtAsc(OrderStatus.PENDING)
                .stream()
                .map(orderMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderCreateResponse> getMyOrders(OrderStatus status) {
        User customer = authenticatedUserResolver.getCurrentUser();
        List<Order> orders = status == null
                ? orderRepository.findByCustomer_IdOrderByCreatedAtDesc(customer.getId())
                : orderRepository.findByCustomer_IdAndOrderStatusOrderByCreatedAtDesc(customer.getId(), status);

        return orders.stream()
                .map(orderMapper::toResponse)
                .toList();
    }
}
