package com.ondemandmonitoring.order.service.impl;

import com.ondemandmonitoring.common.exception.ApiException;
import com.ondemandmonitoring.common.exception.ErrorCode;
import com.ondemandmonitoring.order.domain.Order;
import com.ondemandmonitoring.order.domain.OrderDeliverable;
import com.ondemandmonitoring.order.dto.request.OrderCreateRequest;
import com.ondemandmonitoring.order.dto.request.OrderDeliverableRequest;
import com.ondemandmonitoring.order.dto.request.FlightAreaReviewRequest;
import com.ondemandmonitoring.order.dto.request.LegalReviewResolutionRequest;
import com.ondemandmonitoring.order.dto.OrderAuthorizationDocument;
import com.ondemandmonitoring.order.dto.response.OrderCreateResponse;
import com.ondemandmonitoring.order.enums.OrderDeliveryMethod;
import com.ondemandmonitoring.order.enums.OrderPermitStatus;
import com.ondemandmonitoring.order.enums.OrderRecurrenceType;
import com.ondemandmonitoring.order.enums.OrderResultFormat;
import com.ondemandmonitoring.order.enums.OrderWeatherFallback;
import com.ondemandmonitoring.order.enums.OrderStatus;
import com.ondemandmonitoring.order.enums.DocumentVerificationStatus;
import com.ondemandmonitoring.order.enums.LegalReviewStatus;
import com.ondemandmonitoring.order.mapper.OrderMapper;
import com.ondemandmonitoring.order.repository.OrderRepository;
import com.ondemandmonitoring.order.service.IOrderService;
import com.ondemandmonitoring.order.service.support.OrderLookup;
import com.ondemandmonitoring.order.service.validation.OrderApprovalValidator;
import com.ondemandmonitoring.order.service.IOrderChecklistSnapshotService;
import com.ondemandmonitoring.order.util.GeoReader;
import com.ondemandmonitoring.order.util.ServiceAreaPolicy;
import com.ondemandmonitoring.review.service.OrderReviewRecordInitializer;
import com.ondemandmonitoring.service.domain.DeliverableType;
import com.ondemandmonitoring.service.domain.Service;
import com.ondemandmonitoring.service.repository.DeliverableTypeRepository;
import com.ondemandmonitoring.service.repository.ServiceDeliverableRepository;
import com.ondemandmonitoring.service.repository.ServiceRepository;
import com.ondemandmonitoring.user.domain.User;
import com.ondemandmonitoring.user.repository.CustomerProfileRepository;
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

import com.ondemandmonitoring.order.service.impl.*;

@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class OrderService implements IOrderService {
    OrderCreationService orderCreationService;
    OrderReviewService orderReviewService;
    OrderCustomerUpdateService orderCustomerUpdateService;
    OrderQueryService orderQueryService;

    @Override
    @Transactional
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public OrderCreateResponse approveOrder(String orderId) {
        return orderReviewService.approveOrder(orderId);
    }

    @Override
    @Transactional
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public OrderCreateResponse reviewFlightArea(String orderId, FlightAreaReviewRequest request) {
        return orderReviewService.reviewFlightArea(orderId, request);
    }

    @Override
    @Transactional
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public OrderCreateResponse resolveLegalReview(String orderId, LegalReviewResolutionRequest request) {
        return orderReviewService.resolveLegalReview(orderId, request);
    }

    @Override
    @Transactional
    @PreAuthorize("hasRole('CUSTOMER')")
    public OrderCreateResponse createOrder(OrderCreateRequest request) {
        return orderCreationService.createOrder(request);
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("@orderAuthorizationService.canViewOrder(#orderId)")
    public OrderCreateResponse getOrderById(String orderId) {
        return orderQueryService.getOrderById(orderId);
    }

    @Override
    @Transactional
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public void rejectOrder(String orderId, String reason) {
        orderReviewService.rejectOrder(orderId, reason);
    }

    @Override
    @Transactional
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public void requestMoreInformation(String orderId, String reason, String requestedUpdateScope) {
        orderReviewService.requestMoreInformation(orderId, reason, requestedUpdateScope);
    }

    @Override
    @Transactional
    @PreAuthorize("hasRole('CUSTOMER')")
    public OrderCreateResponse submitAdditionalInformation(String orderId,
            com.ondemandmonitoring.order.dto.request.OrderAdditionalInformationRequest request) {
        return orderCustomerUpdateService.submitAdditionalInformation(orderId, request);
    }

    @Override
    @Transactional
    @PreAuthorize("hasRole('CUSTOMER')")
    public OrderCreateResponse updateCustomerOrder(String orderId,
            com.ondemandmonitoring.order.dto.request.OrderCustomerUpdateRequest request) {
        return orderCustomerUpdateService.updateCustomerOrder(orderId, request);
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public List<OrderCreateResponse> getPendingOrders() {
        return orderQueryService.getPendingOrders();
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public List<OrderCreateResponse> getApprovedOrders() {
        return orderQueryService.getApprovedOrders();
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public List<OrderCreateResponse> getManagedOrders(OrderStatus status) {
        return orderQueryService.getManagedOrders(status);
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('CUSTOMER')")
    public List<OrderCreateResponse> getMyOrders(OrderStatus status) {
        return orderQueryService.getMyOrders(status);
    }

}
