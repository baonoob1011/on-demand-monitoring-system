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
public class OrderCreationWorkflow {
    private static final DateTimeFormatter ORDER_CODE_DATE_FORMATTER = DateTimeFormatter.BASIC_ISO_DATE;
    private static final int MINIMUM_FLIGHT_NOTICE_DAYS = 7;
    static final String TERMS_VERSION = "2026-10";
    static final java.util.List<Integer> ALLOWED_RETENTION_DAYS = java.util.List.of(30,90,180,365);
    static final int MAX_PERMIT_FILES = 3;
    static final long MAX_PERMIT_FILE_BYTES = 5L * 1024 * 1024;
    static final int MAX_PERMIT_DATA_URL_CHARS = 7_500_000;
    OrderRepository orderRepository;
    ServiceRepository serviceRepository;
    DeliverableTypeRepository deliverableTypeRepository;
    ServiceDeliverableRepository serviceDeliverableRepository;
    PreferredTimeRepository preferredTimeRepository;
    AuthenticatedUserResolver authenticatedUserResolver;
    OrderMapper orderMapper;
    IOrderChecklistSnapshotService checklistSnapshots;
    OrderReviewRecordInitializer reviewRecords;
    OrderResponseAssembler responses;

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
        LocalDate earliestFlightDate = LocalDate.now().plusDays(MINIMUM_FLIGHT_NOTICE_DAYS);
        if (request.getPreferredDateFrom().isBefore(earliestFlightDate)) {
            throw new ApiException(ErrorCode.INVALID_REQUEST,
                    "preferredDateFrom must be at least 7 days after the request date");
        }
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
        if (!Boolean.TRUE.equals(request.getMonitoringAuthorizationConfirmed())) {
            throw new ApiException(ErrorCode.INVALID_REQUEST,
                    "Monitoring authorization must be confirmed");
        }
        List<OrderAuthorizationDocument> authorizationDocuments =
                validateAuthorizationDocuments(request.getMonitoringAuthorizationDocuments());
        int retentionDays = request.getDataRetentionDays();
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

        // 5b. The customer self-checks the official portal and declares the result.
        OrderPermitStatus permitStatus = request.getPermitStatus() == null
                ? OrderPermitStatus.NONE : request.getPermitStatus();
        if (permitStatus == OrderPermitStatus.HAVE_PERMIT
                && (request.getPermitNumber() == null || request.getPermitNumber().isBlank())) {
            throw new ApiException(ErrorCode.INVALID_REQUEST,
                    "permitNumber is required when permitStatus is HAVE_PERMIT");
        }
        if (permitStatus == OrderPermitStatus.HAVE_PERMIT) {
            validatePermitEvidence(request);
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
        order.setPermitRequired(permitStatus != OrderPermitStatus.NONE);
        order.setPermitZoneName(null);
        order.setMonitoringAuthorizationConfirmed(true);
        order.setMonitoringAuthorizationDocuments(authorizationDocuments);
        if (permitStatus != OrderPermitStatus.HAVE_PERMIT) {
            order.setPermitNumber(null);
        }
        if (permitStatus != OrderPermitStatus.HAVE_PERMIT) {
            order.setPermitExpiresOn(null);
            order.setPermitDocuments(null);
        }
        order.setRecurrenceType(recurrenceType);
        if (recurrenceType == OrderRecurrenceType.NONE) {
            order.setRecurrenceOccurrences(null);
        }
        if (order.getWeatherFallback() == null) {
            order.setWeatherFallback(OrderWeatherFallback.CONTACT_CUSTOMER);
        }
        if (order.getFlightPurpose() == null) {
            order.setFlightPurpose(com.ondemandmonitoring.order.enums.OrderFlightPurpose.DEFAULT);
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
        reviewRecords.initialize(savedOrder);
        orderRepository.flush();
        OrderCreateResponse response = orderMapper.responses.toResponse(savedOrder);
        responses.responses.enrichCustomerProfile(response, savedOrder);
        response.setChecklistItems(items);
        return response;
    }

    private static List<OrderAuthorizationDocument> validateAuthorizationDocuments(
            List<OrderAuthorizationDocument> documents) {
        if (documents == null || documents.isEmpty()) return List.of();
        if (documents.size() > 10) {
            throw new ApiException(ErrorCode.INVALID_REQUEST,
                    "At most 10 authorization documents are allowed");
        }
        return documents.stream().map(document -> {
            if (document == null || document.documentType() == null
                    || document.documentName() == null || document.documentName().isBlank()
                    || document.documentName().length() > 160
                    || document.fileName() == null || document.fileName().isBlank()
                    || document.contentType() == null
                    || !(document.contentType().equals("application/pdf")
                        || document.contentType().equals("image/jpeg")
                        || document.contentType().equals("image/png"))
                    || document.sizeBytes() <= 0 || document.sizeBytes() > 5 * 1024 * 1024
                    || document.dataUrl() == null || document.dataUrl().isBlank()) {
                throw new ApiException(ErrorCode.INVALID_REQUEST,
                        "Invalid monitoring authorization document");
            }
            return new OrderAuthorizationDocument(
                    document.id(), document.documentType(), document.documentName().trim(),
                    document.fileName(), document.contentType(), document.sizeBytes(),
                    document.dataUrl(),
                    document.notes() == null || document.notes().isBlank()
                            ? null : document.notes().trim(),
                    DocumentVerificationStatus.PENDING, null, null, null);
        }).toList();
    }

    private static void validatePermitEvidence(OrderCreateRequest request) {
        var documents = request.getPermitDocuments();
        if (documents == null || documents.isEmpty()) {
            throw new ApiException(ErrorCode.INVALID_REQUEST,
                    "Attach the flight permit file when permitStatus is HAVE_PERMIT");
        }
        if (documents.size() > MAX_PERMIT_FILES) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "At most " + MAX_PERMIT_FILES + " permit files");
        }
        for (var document : documents) {
            String type = document.contentType() == null ? "" : document.contentType().toLowerCase();
            boolean allowedType = type.equals("application/pdf") || type.startsWith("image/");
            if (document.fileName() == null || document.fileName().isBlank() || !allowedType) {
                throw new ApiException(ErrorCode.INVALID_REQUEST, "Permit files must be PDF or image");
            }
            if (document.sizeBytes() <= 0 || document.sizeBytes() > MAX_PERMIT_FILE_BYTES
                    || document.dataUrl() == null || document.dataUrl().isBlank()
                    || document.dataUrl().length() > MAX_PERMIT_DATA_URL_CHARS) {
                throw new ApiException(ErrorCode.INVALID_REQUEST, "Permit file is empty or larger than 5 MB");
            }
        }
        if (request.getPermitExpiresOn() == null) {
            throw new ApiException(ErrorCode.INVALID_REQUEST,
                    "permitExpiresOn is required when permitStatus is HAVE_PERMIT");
        }
        if (request.getPermitExpiresOn().isBefore(request.getPreferredDateTo())) {
            throw new ApiException(ErrorCode.INVALID_REQUEST,
                    "The permit expires before the preferred flight date");
        }
    }

    private static <T> java.util.List<T> distinctOrDefault(java.util.List<T> values, T fallback) {
        if (values == null || values.isEmpty()) return new java.util.ArrayList<>(java.util.List.of(fallback));
        return new java.util.ArrayList<>(new java.util.LinkedHashSet<>(values));
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
