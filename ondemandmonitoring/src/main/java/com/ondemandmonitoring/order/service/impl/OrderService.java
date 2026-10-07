package com.ondemandmonitoring.order.service.impl;

import com.ondemandmonitoring.common.exception.ApiException;
import com.ondemandmonitoring.common.exception.ErrorCode;
import com.ondemandmonitoring.mission.enums.MissionResultApprovalStatus;
import com.ondemandmonitoring.mission.repository.MissionResultRepository;
import com.ondemandmonitoring.order.domain.Order;
import com.ondemandmonitoring.order.domain.OrderDeliverable;
import com.ondemandmonitoring.order.dto.request.OrderCreateRequest;
import com.ondemandmonitoring.order.dto.request.OrderDeliverableRequest;
import com.ondemandmonitoring.order.dto.response.OrderCreateResponse;
import com.ondemandmonitoring.order.enums.OrderDeliveryMethod;
import com.ondemandmonitoring.order.enums.OrderPermitStatus;
import com.ondemandmonitoring.order.enums.OrderPriority;
import com.ondemandmonitoring.order.enums.OrderRecurrenceType;
import com.ondemandmonitoring.order.enums.OrderResultFormat;
import com.ondemandmonitoring.order.enums.OrderWeatherFallback;
import com.ondemandmonitoring.order.enums.OrderStatus;
import com.ondemandmonitoring.order.mapper.OrderMapper;
import com.ondemandmonitoring.order.repository.OrderRepository;
import com.ondemandmonitoring.order.service.IOrderService;
import com.ondemandmonitoring.order.service.IOrderChecklistSnapshotService;
import com.ondemandmonitoring.order.util.AirspacePolicy;
import com.ondemandmonitoring.order.util.GeoReader;
import com.ondemandmonitoring.order.util.ServiceAreaPolicy;
import com.ondemandmonitoring.service.domain.DeliverableType;
import com.ondemandmonitoring.service.domain.Service;
import com.ondemandmonitoring.service.repository.DeliverableTypeRepository;
import com.ondemandmonitoring.service.repository.ServiceDeliverableRepository;
import com.ondemandmonitoring.service.repository.ServiceRepository;
import com.ondemandmonitoring.user.domain.User;
import com.ondemandmonitoring.user.service.AuthenticatedUserResolver;
import com.ondemandmonitoring.warehouse.domain.PreferredTime;
import com.ondemandmonitoring.warehouse.repository.PreferredTimeRepository;
import java.util.ArrayList;
import java.util.List;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.prepost.PreAuthorize;

@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class OrderService implements IOrderService {

    private static final DateTimeFormatter ORDER_CODE_DATE_FORMATTER = DateTimeFormatter.BASIC_ISO_DATE;

    OrderRepository orderRepository;
    ServiceRepository serviceRepository;
    DeliverableTypeRepository deliverableTypeRepository;
    ServiceDeliverableRepository serviceDeliverableRepository;
    PreferredTimeRepository preferredTimeRepository;
    AuthenticatedUserResolver authenticatedUserResolver;
    OrderMapper orderMapper;
    MissionResultRepository missionResultRepository;
    IOrderChecklistSnapshotService checklistSnapshots;

    @Override
    @Transactional
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public OrderCreateResponse approveOrder(String orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Order not found: " + orderId));

        if (order.getOrderStatus() == OrderStatus.APPROVED) {
            return toResponse(order);
        }

        if (order.getOrderStatus() != OrderStatus.PENDING) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "Only PENDING orders can be approved");
        }

        order.setOrderStatus(OrderStatus.APPROVED);
        order.setReviewBy(authenticatedUserResolver.getCurrentUser());
        order.setReviewAt(Instant.now());
        orderRepository.save(order);

        return toResponse(order);
    }

    @Override
    @Transactional
    @PreAuthorize("hasRole('CUSTOMER')")
    public OrderCreateResponse createOrder(OrderCreateRequest request) {

        // 1. Resolve Customer from logged-in user session
        User customer = authenticatedUserResolver.getCurrentUser();

        // 2. Validate & fetch Service
        Service service = serviceRepository.findByIdForUpdate(request.getServiceId())
                .orElseThrow(() -> new ApiException(ErrorCode.SERVICE_NOT_FOUND,
                        "Service not found with id: " + request.getServiceId()));
        if (!Boolean.TRUE.equals(service.getIsActive())) {
            throw new ApiException(ErrorCode.SERVICE_INACTIVE);
        }

        // 3. Validate & fetch Preferred Time
        PreferredTime preferredTime = preferredTimeRepository.findById(request.getPreferredTimeId())
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND,
                        "Preferred time not found with id: " + request.getPreferredTimeId()));

        // 4. Validate Date Range
        if (request.getPreferredDateFrom().isAfter(request.getPreferredDateTo())) {
            throw new ApiException(ErrorCode.INVALID_REQUEST,
                    "preferredDateFrom must be before or equal to preferredDateTo");
        }

        // 4b. Validate result deadline and recurrence
        if (request.getResultDeadline() != null
                && request.getResultDeadline().isBefore(request.getPreferredDateTo())) {
            throw new ApiException(ErrorCode.INVALID_REQUEST,
                    "resultDeadline must not be before preferredDateTo");
        }
        OrderRecurrenceType recurrenceType = request.getRecurrenceType() == null
                ? OrderRecurrenceType.NONE
                : request.getRecurrenceType();
        if (recurrenceType != OrderRecurrenceType.NONE && request.getRecurrenceOccurrences() == null) {
            throw new ApiException(ErrorCode.INVALID_REQUEST,
                    "recurrenceOccurrences is required when the order repeats");
        }

        // 4c. Delivery options and terms
        if (!Boolean.TRUE.equals(request.getTermsAccepted())) {
            throw new ApiException(ErrorCode.INVALID_REQUEST,
                    "Terms and data-security commitment must be accepted");
        }
        int retentionDays = request.getDataRetentionDays() == null
                ? DEFAULT_RETENTION_DAYS
                : request.getDataRetentionDays();
        if (!ALLOWED_RETENTION_DAYS.contains(retentionDays)) {
            throw new ApiException(ErrorCode.INVALID_REQUEST,
                    "dataRetentionDays must be one of " + ALLOWED_RETENTION_DAYS);
        }

        // 5. Convert & validate GeoJSON geometries
        if (!Double.isFinite(request.getLatitude()) || !Double.isFinite(request.getLongitude())
                || Math.abs(request.getLatitude()) > 90 || Math.abs(request.getLongitude()) > 180) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "Location must be valid WGS84 GPS coordinates");
        }
        Point location = GeoReader.createPoint(request.getLongitude(), request.getLatitude());
        if (location == null) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "Invalid location coordinates");
        }
        Polygon targetArea = GeoReader.readPolygonFromCoverageArea(request.getCoverageArea());
        if (targetArea == null) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "Invalid coverageArea GeoJSON");
        }
        ServiceAreaPolicy.assertSupported(location, targetArea);

        // 5b. Airspace permit (airports etc.) must be answered by the customer
        var permitZone = AirspacePolicy.findPermitZone(location, targetArea);
        OrderPermitStatus permitStatus = request.getPermitStatus() == null
                ? OrderPermitStatus.NONE : request.getPermitStatus();
        if (permitZone.isPresent()) {
            if (permitStatus == OrderPermitStatus.NONE) {
                throw new ApiException(ErrorCode.INVALID_REQUEST,
                        "Area is inside airspace requiring a permit (" + permitZone.get().name()
                                + "). Provide permitStatus HAVE_PERMIT or NEED_SUPPORT.");
            }
            if (permitStatus == OrderPermitStatus.HAVE_PERMIT
                    && (request.getPermitNumber() == null || request.getPermitNumber().isBlank())) {
                throw new ApiException(ErrorCode.INVALID_REQUEST,
                        "permitNumber is required when permitStatus is HAVE_PERMIT");
            }
        }

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
        order.setPermitStatus(permitStatus);
        order.setPermitRequired(permitZone.isPresent());
        order.setPermitZoneName(permitZone.map(AirspacePolicy.PermitZone::name).orElse(null));
        if (permitStatus != OrderPermitStatus.HAVE_PERMIT) {
            order.setPermitNumber(null);
        }
        order.setRecurrenceType(recurrenceType);
        if (recurrenceType == OrderRecurrenceType.NONE) {
            order.setRecurrenceOccurrences(null);
        }
        if (order.getWeatherFallback() == null) {
            order.setWeatherFallback(OrderWeatherFallback.CONTACT_CUSTOMER);
        }
        if (order.getPriority() == null) {
            order.setPriority(OrderPriority.NORMAL);
        }
        order.setResultFormats(distinctOrDefault(request.getResultFormats(), OrderResultFormat.PHOTO));
        order.setDeliveryMethods(distinctOrDefault(request.getDeliveryMethods(), OrderDeliveryMethod.DOWNLOAD));
        order.setDataRetentionDays(retentionDays);
        order.setTermsAcceptedAt(java.time.Instant.now());
        order.setTermsVersion(TERMS_VERSION);
        order.setOrderCode(generateUniqueOrderCode());
        order.setReviewBy(null);
        order.setReviewAt(null);

        for (OrderDeliverable od : orderDeliverables) {
            od.setOrder(order);
        }
        order.setDeliverables(orderDeliverables);

        Order savedOrder = orderRepository.save(order);
        var items = checklistSnapshots.capture(savedOrder, request.getChecklistItems());
        orderRepository.flush();
        OrderCreateResponse response = orderMapper.toResponse(savedOrder);
        response.setChecklistItems(items);
        return response;
    }

    @Override
    @Transactional
    @PreAuthorize("@orderAuthorizationService.canViewOrder(#orderId)")
    public OrderCreateResponse getOrderById(String orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Order not found: " + orderId));
        return toResponse(syncCompletedOrder(order));
    }

    @Override
    @Transactional
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
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
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public List<OrderCreateResponse> getPendingOrders() {
        return toResponses(orderRepository.findByOrderStatusOrderByCreatedAtAsc(OrderStatus.PENDING));
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public List<OrderCreateResponse> getApprovedOrders() {
        return toResponses(orderRepository.findByOrderStatusOrderByCreatedAtAsc(OrderStatus.APPROVED));
    }

    @Override
    @Transactional
    @PreAuthorize("hasRole('CUSTOMER')")
    public List<OrderCreateResponse> getMyOrders(OrderStatus status) {
        User customer = authenticatedUserResolver.getCurrentUser();
        List<Order> orders = status == null
                ? orderRepository.findByCustomer_IdOrderByCreatedAtDesc(customer.getId())
                : orderRepository.findByCustomer_IdAndOrderStatusOrderByCreatedAtDesc(customer.getId(), status);

        return toResponses(orders.stream()
                .map(this::syncCompletedOrder)
                .toList());
    }

    private OrderCreateResponse toResponse(Order order) {
        return toResponses(List.of(order)).getFirst();
    }

    private List<OrderCreateResponse> toResponses(List<Order> orders) {
        var snapshots = checklistSnapshots.getByOrders(orders.stream().map(Order::getId).toList());
        return orders.stream().map(order -> {
            var response = orderMapper.toResponse(order);
            response.setChecklistItems(snapshots.getOrDefault(order.getId(), List.of()));
            return response;
        }).toList();
    }

    /** Version recorded with the customer's acceptance of the terms and security commitment. */
    static final String TERMS_VERSION = "2026-10";
    static final int DEFAULT_RETENTION_DAYS = 90;
    static final java.util.List<Integer> ALLOWED_RETENTION_DAYS = java.util.List.of(30, 90, 180, 365);

    /** Keeps the customer's order of choice, drops duplicates, falls back to one default. */
    private static <T> java.util.List<T> distinctOrDefault(java.util.List<T> values, T fallback) {
        if (values == null || values.isEmpty()) return new java.util.ArrayList<>(java.util.List.of(fallback));
        return new java.util.ArrayList<>(new java.util.LinkedHashSet<>(values));
    }

    private Order syncCompletedOrder(Order order) {
        if (order.getOrderStatus() == OrderStatus.COMPLETED
                || order.getOrderStatus() == OrderStatus.REJECTED
                || order.getOrderStatus() == OrderStatus.CANCELLED) {
            return order;
        }
        boolean approvedResult = missionResultRepository.existsByMission_Order_IdAndApprovalStatus(
                order.getId(), MissionResultApprovalStatus.APPROVED);
        if (approvedResult) {
            order.setOrderStatus(OrderStatus.COMPLETED);
            return orderRepository.save(order);
        }
        return order;
    }

    private String generateUniqueOrderCode() {
        for (int attempt = 0; attempt < 10; attempt++) {
            String orderCode = generateOrderCode();
            if (!orderRepository.existsByOrderCode(orderCode)) {
                return orderCode;
            }
        }
        throw new ApiException(ErrorCode.INVALID_REQUEST, "Unable to generate a unique order code");
    }

    private String generateOrderCode() {
        String datePart = LocalDate.now().format(ORDER_CODE_DATE_FORMATTER);
        String suffix = UUID.randomUUID().toString()
                .replace("-", "")
                .substring(0, 6)
                .toUpperCase();
        return "ORD-" + datePart + "-" + suffix;
    }
}
