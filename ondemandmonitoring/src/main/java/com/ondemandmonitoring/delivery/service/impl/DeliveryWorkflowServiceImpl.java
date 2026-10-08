package com.ondemandmonitoring.delivery.service.impl;

import com.ondemandmonitoring.common.exception.ApiException;
import com.ondemandmonitoring.common.exception.ErrorCode;
import com.ondemandmonitoring.delivery.domain.OrderDelivery;
import com.ondemandmonitoring.delivery.dto.request.ReleasePreviewRequest;
import com.ondemandmonitoring.delivery.dto.request.RevisionRequest;
import com.ondemandmonitoring.delivery.dto.response.DeliveryAssetResponse;
import com.ondemandmonitoring.delivery.dto.response.DeliveryResponse;
import com.ondemandmonitoring.delivery.enums.DeliveryStatus;
import com.ondemandmonitoring.delivery.repository.OrderDeliveryRepository;
import com.ondemandmonitoring.delivery.service.IDeliveryWorkflowService;
import com.ondemandmonitoring.finance.domain.Invoice;
import com.ondemandmonitoring.finance.enums.InvoiceStatus;
import com.ondemandmonitoring.finance.enums.QuoteStatus;
import com.ondemandmonitoring.finance.repository.InvoiceRepository;
import com.ondemandmonitoring.media.domain.MediaAsset;
import com.ondemandmonitoring.media.domain.MediaStatus;
import com.ondemandmonitoring.media.repository.MediaAssetRepository;
import com.ondemandmonitoring.media.service.IMediaObjectStorage;
import com.ondemandmonitoring.mission.domain.Mission;
import com.ondemandmonitoring.mission.enums.MissionStatus;
import com.ondemandmonitoring.mission.repository.MissionRepository;
import com.ondemandmonitoring.mission.enums.MissionResultApprovalStatus;
import com.ondemandmonitoring.mission.repository.MissionResultRepository;
import com.ondemandmonitoring.order.domain.Order;
import com.ondemandmonitoring.order.enums.OrderStatus;
import com.ondemandmonitoring.order.repository.OrderRepository;
import com.ondemandmonitoring.role.domain.RoleCode;
import com.ondemandmonitoring.user.domain.User;
import com.ondemandmonitoring.user.service.AuthenticatedUserResolver;
import java.time.Instant;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Transactional implementation of the order delivery state machine.
 *
 * <p>The service is the single authority for advancing delivery state. It also
 * prevents original media from being exposed before both final payment and the
 * manager's explicit release have completed.</p>
 */
@Service
@RequiredArgsConstructor
public class DeliveryWorkflowServiceImpl implements IDeliveryWorkflowService {
    private final OrderDeliveryRepository deliveries;
    private final OrderRepository orders;
    private final MissionRepository missions;
    private final MissionResultRepository missionResults;
    private final InvoiceRepository invoices;
    private final MediaAssetRepository media;
    private final IMediaObjectStorage storage;
    private final AuthenticatedUserResolver currentUser;

    @Override
    @Transactional
    public void markProcessing(String orderId) {
        Order order = orders.findByIdForUpdate(orderId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));
        deliveries.findByOrderIdForUpdate(orderId).orElseGet(() -> create(order));
        keepOrderInProgress(order);
    }

    @Override
    @Transactional
    public void markReadyForManagerReview(String orderId) {
        Order order = orders.findByIdForUpdate(orderId).orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));
        OrderDelivery delivery = deliveries.findByOrderIdForUpdate(orderId).orElseGet(() -> create(order));
        if (delivery.getStatus() == DeliveryStatus.PROCESSING) {
            delivery.setStatus(DeliveryStatus.READY_FOR_MANAGER_REVIEW);
        }
        keepOrderInProgress(order);
    }

    @Override
    @Transactional
    public DeliveryResponse releasePreview(String orderId, ReleasePreviewRequest request) {
        Mission mission = completedMission(orderId);
        if (!isResultApproved(orderId)) {
            throw new ApiException(ErrorCode.DELIVERY_NOT_READY,
                    "Approve the mission result before releasing a customer preview");
        }
        OrderDelivery delivery = locked(orderId);
        if (delivery.getStatus() != DeliveryStatus.READY_FOR_MANAGER_REVIEW
                && delivery.getStatus() != DeliveryStatus.REVISION_REQUESTED) {
            throw new ApiException(ErrorCode.INVALID_DELIVERY_STATE);
        }
        List<MediaAsset> assets = media.findByMissionIdOrderByCapturedAtDesc(mission.getId());
        Set<String> selected = new HashSet<>(request.mediaAssetIds());
        if (selected.isEmpty() || assets.stream().filter(a -> selected.contains(a.getId()))
                .anyMatch(a -> a.getMediaStatus() != MediaStatus.AVAILABLE)
                || assets.stream().noneMatch(a -> selected.contains(a.getId()))) {
            throw new ApiException(ErrorCode.DELIVERY_NOT_READY, "Select at least one approved mission asset");
        }
        if (selected.stream().anyMatch(id -> assets.stream().noneMatch(a -> a.getId().equals(id)))) {
            throw new ApiException(ErrorCode.MEDIA_ACCESS_DENIED);
        }
        assets.forEach(asset -> asset.setPreviewSelected(selected.contains(asset.getId())));
        media.saveAll(assets);
        Instant now = Instant.now();
        delivery.setStatus(DeliveryStatus.CUSTOMER_REVIEW);
        delivery.setDeliveryNotes(trim(request.notes()));
        delivery.setPreviewReleasedAt(now);
        delivery.setPreviewReleasedBy(currentUser.getCurrentUser());
        delivery.setRevisionReason(null);
        delivery.setRevisionRequestedAt(null);
        return response(delivery, mission, assets.stream().filter(MediaAsset::isPreviewSelected).toList(), false);
    }

    @Override
    @Transactional
    public DeliveryResponse acceptResult(String orderId) {
        assertOwner(orderId);
        Mission mission = completedMission(orderId);
        OrderDelivery delivery = locked(orderId);
        if (delivery.getStatus() == DeliveryStatus.FINAL_PAYMENT_PENDING && delivery.getCustomerAcceptedAt() != null) {
            return response(delivery, mission, List.of(), false);
        }
        if (delivery.getStatus() != DeliveryStatus.CUSTOMER_REVIEW) {
            throw new ApiException(delivery.getCustomerAcceptedAt() != null
                    ? ErrorCode.RESULT_ALREADY_ACCEPTED : ErrorCode.RESULT_NOT_READY_FOR_REVIEW);
        }
        Invoice invoice = invoice(orderId);
        if (invoice.getStatus() == InvoiceStatus.PAID) throw new ApiException(ErrorCode.INVOICE_ALREADY_PAID);
        delivery.setCustomerAcceptedAt(Instant.now());
        delivery.setStatus(DeliveryStatus.FINAL_PAYMENT_PENDING);
        return response(delivery, mission, List.of(), false);
    }

    @Override
    @Transactional
    public DeliveryResponse requestRevision(String orderId, RevisionRequest request) {
        assertOwner(orderId);
        Mission mission = completedMission(orderId);
        OrderDelivery delivery = locked(orderId);
        if (delivery.getStatus() != DeliveryStatus.CUSTOMER_REVIEW) throw new ApiException(ErrorCode.REVISION_NOT_ALLOWED);
        if (request.mediaAssetIds() != null && !request.mediaAssetIds().isEmpty()) {
            Set<String> ids = new HashSet<>(request.mediaAssetIds());
            boolean invalid = media.findByMissionIdOrderByCapturedAtDesc(mission.getId()).stream()
                    .filter(a -> ids.contains(a.getId())).count() != ids.size();
            if (invalid) throw new ApiException(ErrorCode.MEDIA_ACCESS_DENIED);
        }
        delivery.setStatus(DeliveryStatus.REVISION_REQUESTED);
        delivery.setRevisionReason(request.reason().trim());
        delivery.setRevisionRequestedAt(Instant.now());
        delivery.setRevisionCount(delivery.getRevisionCount() + 1);
        return response(delivery, mission, List.of(), false);
    }

    @Override
    @Transactional
    public void confirmFinalPayment(String orderId) {
        OrderDelivery delivery = locked(orderId);
        if (delivery.getStatus() == DeliveryStatus.READY_FOR_DELIVERY
                || delivery.getStatus() == DeliveryStatus.DELIVERED) return;
        if (delivery.getStatus() != DeliveryStatus.FINAL_PAYMENT_PENDING) {
            throw new ApiException(ErrorCode.INVALID_DELIVERY_STATE);
        }
        delivery.setStatus(DeliveryStatus.PAYMENT_CONFIRMED);
        delivery.setFinalPaymentConfirmedAt(Instant.now());
        delivery.setStatus(DeliveryStatus.READY_FOR_DELIVERY);
    }

    @Override
    @Transactional(readOnly = true)
    public void requireFinalPaymentEligible(String orderId) {
        Mission mission = completedMission(orderId);
        OrderDelivery delivery = deliveries.findByOrderId(orderId)
                .orElseThrow(() -> new ApiException(ErrorCode.FINAL_PAYMENT_NOT_ALLOWED));
        Invoice invoice = invoice(orderId);
        boolean valid = mission.getStatus() == MissionStatus.COMPLETED
                && delivery.getStatus() == DeliveryStatus.FINAL_PAYMENT_PENDING
                && delivery.getCustomerAcceptedAt() != null
                && invoice.getQuote().getStatus() == QuoteStatus.ACCEPTED_BY_CUSTOMER
                && invoice.getStatus() != InvoiceStatus.PAID && invoice.getRemainingAmount().signum() > 0;
        if (!valid) throw new ApiException(ErrorCode.FINAL_PAYMENT_NOT_ALLOWED);
    }

    @Override
    @Transactional
    public DeliveryResponse releaseOriginals(String orderId) {
        Mission mission = completedMission(orderId);
        Invoice invoice = invoice(orderId);
        if (invoice.getStatus() != InvoiceStatus.PAID) throw new ApiException(ErrorCode.INVOICE_NOT_PAID);
        OrderDelivery delivery = locked(orderId);
        if (delivery.getStatus() == DeliveryStatus.DELIVERED) throw new ApiException(ErrorCode.DELIVERY_ALREADY_RELEASED);
        if (delivery.getStatus() != DeliveryStatus.READY_FOR_DELIVERY) throw new ApiException(ErrorCode.DELIVERY_NOT_READY);
        List<MediaAsset> assets = media.findByMissionIdOrderByCapturedAtDesc(mission.getId()).stream()
                .filter(a -> a.getMediaStatus() == MediaStatus.AVAILABLE).toList();
        if (assets.isEmpty()) throw new ApiException(ErrorCode.DELIVERY_NOT_READY, "No approved originals exist");
        delivery.setStatus(DeliveryStatus.DELIVERED);
        delivery.setOriginalsReleasedAt(Instant.now());
        delivery.setOriginalsReleasedBy(currentUser.getCurrentUser());
        delivery.getOrder().setOrderStatus(OrderStatus.COMPLETED);
        return response(delivery, mission, assets, true);
    }

    @Override @Transactional
    public DeliveryResponse get(String orderId) {
        assertOwnerOrManager(orderId);
        Mission mission = missions.findByOrderId(orderId).orElseThrow(() -> new ApiException(ErrorCode.MISSION_NOT_FOUND));
        OrderDelivery delivery = deliveries.findByOrderId(orderId)
                .orElseGet(() -> bootstrapCompletedDelivery(mission));
        reconcileManagerReviewState(delivery, mission);
        List<MediaAsset> assets = isManager()
                ? media.findByMissionIdOrderByCapturedAtDesc(mission.getId()).stream()
                    .filter(a -> a.getMediaStatus() == MediaStatus.AVAILABLE).toList()
                : List.of();
        return response(delivery, mission, assets, false);
    }

    @Override @Transactional(readOnly = true)
    public DeliveryResponse previews(String orderId) {
        assertOwnerOrManager(orderId);
        OrderDelivery delivery = deliveries.findByOrderId(orderId).orElseThrow(() -> new ApiException(ErrorCode.RESULT_NOT_READY_FOR_REVIEW));
        if (delivery.getPreviewReleasedAt() == null || delivery.getStatus().ordinal() < DeliveryStatus.CUSTOMER_REVIEW.ordinal()) {
            throw new ApiException(ErrorCode.RESULT_NOT_READY_FOR_REVIEW);
        }
        Mission mission = missions.findByOrderId(orderId).orElseThrow(() -> new ApiException(ErrorCode.MISSION_NOT_FOUND));
        List<MediaAsset> assets = media.findByMissionIdOrderByCapturedAtDesc(mission.getId()).stream()
                .filter(MediaAsset::isPreviewSelected).filter(a -> a.getMediaStatus() == MediaStatus.AVAILABLE).toList();
        return response(delivery, mission, assets, false);
    }

    @Override @Transactional(readOnly = true)
    public DeliveryResponse originals(String orderId) {
        assertOwnerOrManager(orderId);
        requireOriginalAccess(orderId);
        OrderDelivery delivery = deliveries.findByOrderId(orderId).orElseThrow();
        Mission mission = missions.findByOrderId(orderId).orElseThrow(() -> new ApiException(ErrorCode.MISSION_NOT_FOUND));
        List<MediaAsset> assets = media.findByMissionIdOrderByCapturedAtDesc(mission.getId()).stream()
                .filter(a -> a.getMediaStatus() == MediaStatus.AVAILABLE).toList();
        return response(delivery, mission, assets, true);
    }

    @Override @Transactional(readOnly = true)
    public void requireOriginalAccess(String orderId) {
        if (isManager()) return;
        assertOwner(orderId);
        Invoice invoice = invoice(orderId);
        if (invoice.getStatus() != InvoiceStatus.PAID) throw new ApiException(ErrorCode.FINAL_PAYMENT_REQUIRED);
        OrderDelivery delivery = deliveries.findByOrderId(orderId).orElseThrow(() -> new ApiException(ErrorCode.DELIVERABLE_NOT_RELEASED));
        if (delivery.getStatus() != DeliveryStatus.DELIVERED) throw new ApiException(ErrorCode.DELIVERABLE_NOT_RELEASED);
    }

    @Override
    @Transactional(readOnly = true)
    public void requireOriginalMissionAccess(String missionId) {
        Mission mission = missions.findByIdWithOrder(missionId)
                .orElseThrow(() -> new ApiException(ErrorCode.MISSION_NOT_FOUND));
        requireOriginalAccess(mission.getOrder().getId());
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> originalAccessibleMissionIds(List<String> missionIds) {
        if (missionIds == null || missionIds.isEmpty()) return List.of();
        String customerId = currentUser.getCurrentUserId();
        return missionIds.stream().filter(missionId -> missions.findByIdWithOrder(missionId)
                .map(Mission::getOrder)
                .filter(order -> order.getCustomer().getId().equals(customerId))
                .filter(order -> invoices.findByOrderId(order.getId())
                        .filter(invoice -> invoice.getStatus() == InvoiceStatus.PAID).isPresent())
                .filter(order -> deliveries.findByOrderId(order.getId())
                        .filter(delivery -> delivery.getStatus() == DeliveryStatus.DELIVERED).isPresent())
                .isPresent()).toList();
    }

    private OrderDelivery locked(String orderId) {
        return deliveries.findByOrderIdForUpdate(orderId).orElseThrow(() -> new ApiException(ErrorCode.DELIVERY_NOT_READY));
    }
    private OrderDelivery create(Order order) {
        OrderDelivery d = new OrderDelivery(); d.setOrder(order); d.setStatus(DeliveryStatus.PROCESSING); return deliveries.save(d);
    }
    private OrderDelivery bootstrapCompletedDelivery(Mission mission) {
        if (mission.getStatus() != MissionStatus.COMPLETED) {
            throw new ApiException(ErrorCode.DELIVERY_NOT_READY);
        }
        Order order = mission.getOrder();
        OrderDelivery delivery = create(order);
        if (isResultApproved(order.getId())) {
            delivery.setStatus(DeliveryStatus.READY_FOR_MANAGER_REVIEW);
        }
        keepOrderInProgress(order);
        return delivery;
    }
    private void reconcileManagerReviewState(OrderDelivery delivery, Mission mission) {
        if (delivery.getStatus() == DeliveryStatus.READY_FOR_MANAGER_REVIEW
                && !isResultApproved(mission.getOrder().getId())) {
            delivery.setStatus(DeliveryStatus.PROCESSING);
        } else if (delivery.getStatus() == DeliveryStatus.PROCESSING
                && isResultApproved(mission.getOrder().getId())) {
            delivery.setStatus(DeliveryStatus.READY_FOR_MANAGER_REVIEW);
        }
    }
    private boolean isResultApproved(String orderId) {
        return missionResults.existsByMission_Order_IdAndApprovalStatus(
                orderId, MissionResultApprovalStatus.APPROVED);
    }
    private void keepOrderInProgress(Order order) {
        if (order.getOrderStatus() != OrderStatus.CANCELLED && order.getOrderStatus() != OrderStatus.REJECTED) {
            order.setOrderStatus(OrderStatus.IN_PROGRESS);
        }
    }
    private Mission completedMission(String orderId) {
        Mission mission = missions.findByOrderId(orderId).orElseThrow(() -> new ApiException(ErrorCode.MISSION_NOT_FOUND));
        if (mission.getStatus() != MissionStatus.COMPLETED) throw new ApiException(ErrorCode.MISSION_NOT_COMPLETED);
        return mission;
    }
    private Invoice invoice(String orderId) {
        return invoices.findByOrderId(orderId).orElseThrow(() -> new ApiException(ErrorCode.INVOICE_NOT_FOUND));
    }
    private void assertOwner(String orderId) {
        Order order = orders.findById(orderId).orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));
        if (!order.getCustomer().getId().equals(currentUser.getCurrentUserId())) throw new ApiException(ErrorCode.ACCESS_DENIED);
    }
    private void assertOwnerOrManager(String orderId) { if (!isManager()) assertOwner(orderId); }
    private boolean isManager() {
        User user = currentUser.getCurrentUser();
        return user.getRole() != null && (user.getRole().getCode() == RoleCode.MANAGER || user.getRole().getCode() == RoleCode.ADMIN);
    }
    private DeliveryResponse response(OrderDelivery d, Mission mission, List<MediaAsset> assets, boolean downloadable) {
        Invoice invoice = invoice(d.getOrder().getId());
        Instant expires = Instant.now().plusSeconds(storage.presignedUrlExpiresSeconds());
        List<DeliveryAssetResponse> mapped = assets.stream().map(a -> new DeliveryAssetResponse(a.getId(), a.getType(),
                a.getOriginalFileName(), a.getFileSize(), storage.createPresignedGetUrl(a.getS3Bucket(), a.getS3Key()),
                expires, downloadable)).toList();
        return new DeliveryResponse(d.getOrder().getId(), d.getStatus(), mission.getStatus(), d.getOrder().getOrderStatus(),
                invoice.getStatus(), invoice.getTotalAmount(), invoice.getPaidAmount(), invoice.getRemainingAmount(),
                d.getDeliveryNotes(), d.getPreviewReleasedAt(), d.getCustomerAcceptedAt(), d.getRevisionRequestedAt(),
                d.getRevisionReason(), d.getRevisionCount(), d.getFinalPaymentConfirmedAt(), d.getOriginalsReleasedAt(), mapped);
    }
    private String trim(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}
