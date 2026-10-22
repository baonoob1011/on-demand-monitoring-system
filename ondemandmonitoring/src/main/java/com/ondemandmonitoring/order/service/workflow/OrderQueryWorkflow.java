package com.ondemandmonitoring.order.service.workflow;

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

import com.ondemandmonitoring.order.service.support.OrderLookup;
import com.ondemandmonitoring.order.service.validation.OrderApprovalValidator;

@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class OrderQueryWorkflow {

    OrderRepository orderRepository;
    AuthenticatedUserResolver authenticatedUserResolver;
    OrderResponseAssembler responses;
    OrderLookup orderLookup;

    public OrderCreateResponse getOrderById(String orderId) {
        Order order = orderLookup.getRequired(orderId);
        return responses.toResponse(order);
    }

    public List<OrderCreateResponse> getPendingOrders() {
        return responses.toResponses(orderRepository.findByOrderStatusOrderByCreatedAtAsc(OrderStatus.PENDING));
    }

    public List<OrderCreateResponse> getApprovedOrders() {
        return responses.toResponses(orderRepository.findByOrderStatusOrderByCreatedAtAsc(OrderStatus.APPROVED));
    }

    public List<OrderCreateResponse> getManagedOrders(OrderStatus status) {
        List<Order> orders = status == null
                ? orderRepository.findAll(org.springframework.data.domain.Sort.by(
                        org.springframework.data.domain.Sort.Direction.DESC, "createdAt"))
                : orderRepository.findByOrderStatusOrderByCreatedAtAsc(status).reversed();
        return responses.toResponses(orders);
    }

    public List<OrderCreateResponse> getMyOrders(OrderStatus status) {
        User customer = authenticatedUserResolver.getCurrentUser();
        List<Order> orders = status == null
                ? orderRepository.findByCustomer_IdOrderByCreatedAtDesc(customer.getId())
                : orderRepository.findByCustomer_IdAndOrderStatusOrderByCreatedAtDesc(customer.getId(), status);

        return responses.toResponses(orders);
    }
}
