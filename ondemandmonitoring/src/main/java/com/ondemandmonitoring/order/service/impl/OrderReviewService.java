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
import com.ondemandmonitoring.order.service.support.OrderResponseAssembler;
import com.ondemandmonitoring.order.service.validation.OrderApprovalValidator;

@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class OrderReviewService {

    OrderRepository orderRepository;
    AuthenticatedUserResolver authenticatedUserResolver;
    OrderResponseAssembler responses;
    OrderLookup orderLookup;
    OrderApprovalValidator orderApprovalValidator;

    public OrderCreateResponse approveOrder(String orderId) {
        Order order = orderLookup.getRequired(orderId);

        if (order.getOrderStatus() == OrderStatus.APPROVED) {
            return responses.toResponse(order);
        }

        orderApprovalValidator.validate(order);

        order.setOrderStatus(OrderStatus.APPROVED);
        order.setReviewBy(authenticatedUserResolver.getCurrentUser());
        order.setReviewAt(Instant.now());
        orderRepository.save(order);

        return responses.toResponse(order);
    }

    public OrderCreateResponse reviewFlightArea(String orderId, FlightAreaReviewRequest request) {
        Order order = orderLookup.getRequired(orderId);
        if (order.getOrderStatus() != OrderStatus.PENDING) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "Only PENDING orders can be reviewed");
        }
        String reason = trimToNull(request.reason());
        String note = trimToNull(request.note());
        if (request.status() != LegalReviewStatus.NOT_REQUIRED
                && request.status() != LegalReviewStatus.PENDING_PERMIT
                && request.status() != LegalReviewStatus.REJECTED) {
            throw new ApiException(ErrorCode.INVALID_REQUEST,
                    "Initial legal review must be NOT_REQUIRED, PENDING_PERMIT, or REJECTED");
        }
        if (request.status() == LegalReviewStatus.REJECTED && reason == null) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "A reason is required for an unsuitable area");
        }
        order.setLegalReviewStatus(request.status());
        order.setFlightAreaReviewReason(reason);
        order.setFlightAreaReviewNote(note);
        if (request.requestedScopeFeasible() != null) {
            order.setRequestedScopeFeasible(request.requestedScopeFeasible());
        }
        order.setFlightAreaReviewedBy(authenticatedUserResolver.getCurrentUser());
        order.setFlightAreaReviewedAt(Instant.now());
        return responses.toResponse(orderRepository.save(order));
    }

    public OrderCreateResponse resolveLegalReview(String orderId, LegalReviewResolutionRequest request) {
        Order order = orderLookup.getRequired(orderId);
        // The permit may still be pending after the order was approved and invoiced.
        if ((order.getOrderStatus() != OrderStatus.PENDING && order.getOrderStatus() != OrderStatus.APPROVED)
                || order.getLegalReviewStatus() != LegalReviewStatus.PENDING_PERMIT) {
            throw new ApiException(ErrorCode.INVALID_REQUEST,
                    "Only an order pending external permit verification can be resolved");
        }
        if (request.status() != LegalReviewStatus.APPROVED
                && request.status() != LegalReviewStatus.REJECTED) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "Resolution must be APPROVED or REJECTED");
        }
        order.setLegalReviewStatus(request.status());
        order.setFlightAreaReviewNote(trimToNull(request.note()));
        order.setFlightAreaReviewedBy(authenticatedUserResolver.getCurrentUser());
        order.setFlightAreaReviewedAt(Instant.now());
        if (request.status() == LegalReviewStatus.REJECTED) {
            order.setFlightAreaReviewReason("Permit verification was rejected");
        }
        return responses.toResponse(orderRepository.save(order));
    }

    public void rejectOrder(String orderId, String reason) {
        Order order = orderLookup.getRequired(orderId);

        if (order.getOrderStatus() != OrderStatus.PENDING) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "Only PENDING orders can be rejected");
        }

        order.setOrderStatus(OrderStatus.REJECTED);
        order.setRejectReason(reason);
        order.setReviewAt(java.time.Instant.now());
        orderRepository.save(order);
    }

    public void requestMoreInformation(String orderId, String reason, String requestedUpdateScope) {
        Order order = orderLookup.getRequired(orderId);

        if (order.getOrderStatus() != OrderStatus.PENDING) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "Only PENDING orders can request more information");
        }

        order.setOrderStatus(OrderStatus.NEEDS_INFO);
        order.setRejectReason(reason);
        order.setRequestedUpdateScope(
                "LOCATION".equalsIgnoreCase(requestedUpdateScope) ? "LOCATION" : "ADDITIONAL_INFORMATION");
        order.setReviewAt(java.time.Instant.now());
        orderRepository.save(order);
    }

    private static String trimToNull(String value) {
        if (value == null || value.isBlank()) return null;
        return value.strip();
    }
}
