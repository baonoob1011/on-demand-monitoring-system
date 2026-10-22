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
public class OrderCustomerUpdateService {

    OrderRepository orderRepository;
    AuthenticatedUserResolver authenticatedUserResolver;
    com.ondemandmonitoring.finance.repository.QuoteRepository quoteRepository;
    OrderMapper orderMapper;
    OrderReviewRecordInitializer reviewRecords;
    OrderResponseAssembler responses;
    OrderLookup orderLookup;

    public OrderCreateResponse submitAdditionalInformation(String orderId,
            com.ondemandmonitoring.order.dto.request.OrderAdditionalInformationRequest request) {
        Order order = orderLookup.getRequired(orderId);
        User customer = authenticatedUserResolver.getCurrentUser();
        if (order.getCustomer() == null || !order.getCustomer().getId().equals(customer.getId())) {
            throw new ApiException(ErrorCode.ACCESS_DENIED, "This order does not belong to the current customer");
        }
        if (order.getOrderStatus() != OrderStatus.NEEDS_INFO) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "Order is not waiting for additional information");
        }
        if ("LOCATION".equals(order.getRequestedUpdateScope())) {
            throw new ApiException(ErrorCode.INVALID_REQUEST,
                    "This request requires the monitoring location to be updated");
        }
        if (request.siteContactName() != null) order.setSiteContactName(request.siteContactName());
        if (request.siteContactPhone() != null) order.setSiteContactPhone(request.siteContactPhone());
        if (request.accessNotes() != null) order.setAccessNotes(request.accessNotes());
        if (request.monitoringRelationship() != null) {
            order.setMonitoringRelationship(request.monitoringRelationship());
        }
        if (request.monitoringAuthorizationConfirmed() != null) {
            order.setMonitoringAuthorizationConfirmed(request.monitoringAuthorizationConfirmed());
        }
        order.setDescription((order.getDescription() == null ? "" : order.getDescription().strip())
                + "\n\nThông tin khách bổ sung: " + request.information().strip());
        order.setOrderStatus(OrderStatus.PENDING);
        order.setRejectReason(null);
        order.setRequestedUpdateScope(null);
        order.setReviewAt(null);
        reviewRecords.reopenFailed(order, request.information());
        return responses.toResponse(orderRepository.save(order));
    }

    public OrderCreateResponse updateCustomerOrder(String orderId,
            com.ondemandmonitoring.order.dto.request.OrderCustomerUpdateRequest request) {
        Order order = orderLookup.getRequired(orderId);
        User customer = authenticatedUserResolver.getCurrentUser();
        if (order.getCustomer() == null || !order.getCustomer().getId().equals(customer.getId()))
            throw new ApiException(ErrorCode.ACCESS_DENIED, "This order does not belong to the current customer");
        if (order.getOrderStatus() != OrderStatus.PENDING && order.getOrderStatus() != OrderStatus.NEEDS_INFO
                && order.getOrderStatus() != OrderStatus.APPROVED)
            throw new ApiException(ErrorCode.INVALID_REQUEST, "Only unpaid Orders can be edited");
        var acceptedQuote = quoteRepository.findFirstByOrderIdAndStatusInOrderByQuoteVersionDesc(orderId,
                java.util.List.of(com.ondemandmonitoring.finance.enums.QuoteStatus.ACCEPTED_BY_CUSTOMER));
        if (acceptedQuote.isPresent())
            throw new ApiException(ErrorCode.INVALID_REQUEST, "An Order cannot be edited after its quote is accepted");

        boolean submittingLocationCorrection = order.getOrderStatus() == OrderStatus.NEEDS_INFO
                && "LOCATION".equals(order.getRequestedUpdateScope());
        if (submittingLocationCorrection) {
            boolean hasLocation = request.address() != null || request.longitude() != null
                    || request.latitude() != null || request.radiusM() != null
                    || request.coverageArea() != null;
            boolean hasUnrequestedChanges = request.title() != null || request.description() != null
                    || request.usagePurpose() != null || request.flightPurpose() != null
                    || request.altitudeM() != null || request.estimatedLengthM() != null
                    || request.siteContactName() != null || request.siteContactPhone() != null
                    || request.accessNotes() != null || request.monitoringRelationship() != null
                    || request.monitoringAuthorizationConfirmed() != null || request.permitStatus() != null
                    || request.permitNumber() != null || request.permitExpiresOn() != null
                    || request.preferredDateFrom() != null || request.preferredDateTo() != null
                    || request.recurrenceType() != null || request.recurrenceOccurrences() != null
                    || request.weatherFallback() != null || request.resultDeadline() != null
                    || request.resultFormats() != null || request.deliveryMethods() != null
                    || request.dataRetentionDays() != null;
            if (!hasLocation || hasUnrequestedChanges) {
                throw new ApiException(ErrorCode.INVALID_REQUEST,
                        "Only the requested monitoring location can be updated");
            }
        }

        if (request.title() != null && !request.title().isBlank()) order.setTitle(request.title().strip());
        if (request.description() != null) order.setDescription(request.description().strip());
        if (request.address() != null) order.setAddress(request.address().strip());
        if (request.longitude() != null || request.latitude() != null || request.coverageArea() != null) {
            if (request.longitude() == null || request.latitude() == null || request.coverageArea() == null) {
                throw new ApiException(ErrorCode.INVALID_REQUEST,
                        "longitude, latitude and coverageArea must be updated together");
            }
            var point = orderMapper.toPoint(request.longitude(), request.latitude());
            var targetArea = orderMapper.toPolygon(request.coverageArea());
            com.ondemandmonitoring.order.util.ServiceAreaPolicy.assertSupported(point, targetArea);
            order.setPoint(point);
            order.setTargetArea(targetArea);
        }
        if (request.radiusM() != null) {
            order.getDeliverables().forEach(deliverable -> {
                var requirement = new java.util.LinkedHashMap<String, Object>();
                if (deliverable.getRequirement() != null) requirement.putAll(deliverable.getRequirement());
                requirement.put("radiusM", request.radiusM());
                deliverable.setRequirement(requirement);
            });
        }
        if (request.usagePurpose() != null) order.setUsagePurpose(request.usagePurpose());
        if (request.flightPurpose() != null) order.setFlightPurpose(request.flightPurpose());
        if (request.altitudeM() != null) order.setAltitudeM(request.altitudeM());
        if (request.estimatedLengthM() != null) order.setEstimatedLengthM(request.estimatedLengthM());
        if (request.siteContactName() != null) order.setSiteContactName(request.siteContactName().strip());
        if (request.siteContactPhone() != null) order.setSiteContactPhone(request.siteContactPhone().strip());
        if (request.accessNotes() != null) order.setAccessNotes(request.accessNotes().strip());
        if (request.monitoringRelationship() != null) order.setMonitoringRelationship(request.monitoringRelationship());
        if (request.monitoringAuthorizationConfirmed() != null) order.setMonitoringAuthorizationConfirmed(request.monitoringAuthorizationConfirmed());
        if (request.permitStatus() != null) order.setPermitStatus(request.permitStatus());
        if (request.permitNumber() != null) order.setPermitNumber(request.permitNumber().strip());
        if (request.permitExpiresOn() != null) order.setPermitExpiresOn(request.permitExpiresOn());
        if (request.preferredDateFrom() != null) order.setPreferredDateFrom(request.preferredDateFrom());
        if (request.preferredDateTo() != null) order.setPreferredDateTo(request.preferredDateTo());
        if (order.getPreferredDateFrom() != null && order.getPreferredDateTo() != null
                && order.getPreferredDateTo().isBefore(order.getPreferredDateFrom()))
            throw new ApiException(ErrorCode.INVALID_REQUEST, "End date cannot be before start date");
        if (request.recurrenceType() != null) order.setRecurrenceType(request.recurrenceType());
        if (request.recurrenceOccurrences() != null) order.setRecurrenceOccurrences(request.recurrenceOccurrences());
        if (request.weatherFallback() != null) order.setWeatherFallback(request.weatherFallback());
        if (request.resultDeadline() != null) order.setResultDeadline(request.resultDeadline());
        if (order.getResultDeadline() != null && order.getPreferredDateTo() != null
                && order.getResultDeadline().isBefore(order.getPreferredDateTo()))
            throw new ApiException(ErrorCode.INVALID_REQUEST, "Result deadline cannot be before the requested end date");
        if (request.resultFormats() != null && !request.resultFormats().isEmpty()) order.setResultFormats(request.resultFormats());
        if (request.deliveryMethods() != null && !request.deliveryMethods().isEmpty()) order.setDeliveryMethods(request.deliveryMethods());
        if (request.dataRetentionDays() != null) {
            if (!java.util.Set.of(30, 90, 180, 365).contains(request.dataRetentionDays()))
                throw new ApiException(ErrorCode.INVALID_REQUEST, "Data retention must be 30, 90, 180 or 365 days");
            order.setDataRetentionDays(request.dataRetentionDays());
        }
        if (order.getOrderStatus() == OrderStatus.APPROVED) {
            quoteRepository.findFirstByOrderIdAndStatusInOrderByQuoteVersionDesc(orderId,
                    java.util.List.of(com.ondemandmonitoring.finance.enums.QuoteStatus.DRAFT,
                            com.ondemandmonitoring.finance.enums.QuoteStatus.PENDING_REVIEW,
                            com.ondemandmonitoring.finance.enums.QuoteStatus.APPROVED))
                    .ifPresent(quote -> {
                        quote.setStatus(com.ondemandmonitoring.finance.enums.QuoteStatus.SUPERSEDED);
                        quoteRepository.save(quote);
                    });
            order.setOrderStatus(OrderStatus.PENDING);
            order.setReviewBy(null);
            order.setReviewAt(null);
        }
        if (order.getOrderStatus() == OrderStatus.NEEDS_INFO) {
            order.setOrderStatus(OrderStatus.PENDING);
            order.setRejectReason(null);
            order.setRequestedUpdateScope(null);
            order.setReviewAt(null);
        }
        if (submittingLocationCorrection) {
            // The previous legal review belongs to the old coordinates and must
            // never be shown as the verdict for the newly submitted area.
            order.setLegalReviewStatus(LegalReviewStatus.NOT_REVIEWED);
            order.setRequestedScopeFeasible(null);
            order.setFlightAreaReviewReason(null);
            order.setFlightAreaReviewNote(null);
            order.setFlightAreaReviewedBy(null);
            order.setFlightAreaReviewedAt(null);
            order.setPermitRequired(null);
            order.setPermitZoneName(null);
            reviewRecords.reopenFailed(order, "Khách hàng đã cập nhật lại khu vực giám sát");
        }
        return responses.toResponse(orderRepository.save(order));
    }
}
