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
public class OrderResponseAssembler {

    CustomerProfileRepository customerProfileRepository;
    OrderMapper orderMapper;
    IOrderChecklistSnapshotService checklistSnapshots;
    com.ondemandmonitoring.finance.repository.InvoiceRepository invoiceRepository;

    public OrderCreateResponse toResponse(Order order) {
        return toResponses(List.of(order)).getFirst();
    }

    public List<OrderCreateResponse> toResponses(List<Order> orders) {
        var snapshots = checklistSnapshots.getByOrders(orders.stream().map(Order::getId).toList());
        return orders.stream().map(order -> {
            var response = orderMapper.toResponse(order);
            enrichCustomerProfile(response, order);
            response.setChecklistItems(snapshots.getOrDefault(order.getId(), List.of()));
            if (order.getOrderStatus() == OrderStatus.APPROVED) {
                response.setDepositSatisfied(invoiceRepository.findByOrderId(order.getId())
                        .map(invoice -> invoice.getPaidAmount().compareTo(invoice.getDepositAmount()) >= 0)
                        .orElse(false));
            }
            return response;
        }).toList();
    }

    public void enrichCustomerProfile(OrderCreateResponse response, Order order) {
        if (order.getCustomer() == null || order.getCustomer().getId() == null) {
            return;
        }
        customerProfileRepository.findById(order.getCustomer().getId()).ifPresent(profile -> {
            response.setCustomerPhoneNumber(profile.getPhoneNumber());
            response.setCustomerAddress(profile.getAddress());
            response.setCustomerCompanyName(profile.getCompanyName());
            response.setCustomerCitizenIdentificationNumber(
                    profile.getCitizenIdentificationNumber());
            response.setCustomerCompanyTaxCode(profile.getCompanyTaxCode());
        });
    }
}
