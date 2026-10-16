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
import com.ondemandmonitoring.order.enums.RequiredEvidenceType;
import com.ondemandmonitoring.order.mapper.OrderChecklistItemMapper;
import com.ondemandmonitoring.order.repository.OrderChecklistItemRepository;
import com.ondemandmonitoring.order.service.IOrderChecklistSnapshotService;
import com.ondemandmonitoring.service.repository.ServiceRepository;
import com.ondemandmonitoring.pricing.domain.PricingPolicy;
import com.ondemandmonitoring.pricing.enums.ChecklistDifficulty;
import com.ondemandmonitoring.pricing.enums.MediaPricingComponent;
import com.ondemandmonitoring.pricing.repository.PricingPolicyRepository;
import com.ondemandmonitoring.pricing.service.ChecklistPriceCalculator;
import com.ondemandmonitoring.pricing.service.ServicePriceResolver;
import java.math.BigDecimal;
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
    private final PricingPolicyRepository pricingPolicies;
    private final ChecklistPriceCalculator priceCalculator;
    private final ServicePriceResolver servicePrices;

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
        Map<String, ServiceChecklistRepository.TemplateOverride> overrides = new HashMap<>();
        for (var override : assignments.findTemplateOverrides(service.getId())) overrides.put(override.getChecklistId(), override);
        Set<String> ids = new TreeSet<>();
        for (var item : items) {
            if (item == null) throw invalid("Checklist item cannot be null");
            String source = item.getSourceChecklistId();
            if (source != null) {
                if (source.isBlank())
                    throw new ApiException(ErrorCode.ORDER_CHECKLIST_TEMPLATE_CHANGED);
                if (!ids.add(source)) throw invalid("Duplicate source checklist");
                if (!defaults && (item.getExpectedChecklistVersion() == null || item.getExpectedChecklistVersion() < 0))
                    throw invalid("expectedChecklistVersion is required for a template item");
            } else if (item.getExpectedChecklistVersion() != null) throw invalid("Custom item cannot declare a catalog version");
        }
        if (!defaults) {
            for (var override : overrides.values()) {
                if (override.isRequired() && Boolean.TRUE.equals(override.getChecklistActive()) && !ids.contains(override.getChecklistId()))
                    throw new ApiException(ErrorCode.ORDER_CHECKLIST_REQUIRED_MISSING);
            }
        }
        Map<String, ChecklistDefinition> locked = new HashMap<>();
        for (String id : ids) locked.put(id, definitions.findByIdForUpdate(id)
                .orElseThrow(() -> new ApiException(ErrorCode.ORDER_CHECKLIST_TEMPLATE_CHANGED)));
        List<OrderChecklistItem> entities = new ArrayList<>();
        Set<String> contents = new HashSet<>();
        PricingPolicy policy = pricingPolicies.findEffectiveActive(Instant.now()).stream().findFirst().orElse(null);
        for (var item : items) {
            ChecklistDefinition source = locked.get(item.getSourceChecklistId());
            if (source != null && !Boolean.TRUE.equals(source.getIsActive())) {
                if (defaults) continue;
                throw new ApiException(ErrorCode.ORDER_CHECKLIST_TEMPLATE_CHANGED);
            }
            if (source != null && !defaults && !Objects.equals(source.getVersion(), item.getExpectedChecklistVersion()))
                throw new ApiException(ErrorCode.ORDER_CHECKLIST_TEMPLATE_CHANGED);
            var override = source == null ? null : overrides.get(source.getId());
            boolean custom = source == null;
            boolean packageChecklist = source != null && membership.contains(source.getId());
            boolean explicitEvidence = item.getMinimumEvidenceCount() != null || item.getRequiredEvidenceType() != null;
            boolean explicitMedia = item.getRequestedImageCount() != null || item.getRequestedVideoCount() != null;
            if (explicitEvidence && explicitMedia)
                throw invalid("Use requested media counts or an explicit evidence requirement, not both");
            // Effective configuration = association override, else the checklist definition (custom: policy allowance).
            BigDecimal basePrice = custom ? BigDecimal.ZERO : (override != null && override.getBasePriceOverride() != null
                    ? override.getBasePriceOverride() : source.getBasePrice());
            int includedImages = custom ? (policy == null ? 0 : priceCalculator.customAllowance(policy, MediaPricingComponent.INCLUDED_IMAGE))
                    : (override != null && override.getIncludedImageCountOverride() != null
                    ? override.getIncludedImageCountOverride() : source.getIncludedImageCount());
            int includedVideos = custom ? (policy == null ? 0 : priceCalculator.customAllowance(policy, MediaPricingComponent.INCLUDED_VIDEO))
                    : (override != null && override.getIncludedVideoCountOverride() != null
                    ? override.getIncludedVideoCountOverride() : source.getIncludedVideoCount());
            String profile = custom ? ChecklistPriceCalculator.CUSTOM_PROFILE : ChecklistPriceCalculator.normalizeProfile(
                    override != null && override.getPricingProfileOverride() != null
                            ? override.getPricingProfileOverride() : source.getPricingProfile());
            int requestedImages = item.getRequestedImageCount() == null ? includedImages : item.getRequestedImageCount();
            int requestedVideos = item.getRequestedVideoCount() == null ? includedVideos : item.getRequestedVideoCount();
            if (explicitMedia && requestedImages + requestedVideos < 1)
                throw invalid("A checklist needs at least one image or video");
            var entity = new OrderChecklistItem();
            entity.setPricingSnapshotVersion(1);
            // Baseline money always comes from policy. Custom work is resolved by structured assessment, never a typed quote.
            entity.setManagerQuoteRequired(false);
            entity.setBasePriceSnapshot(basePrice == null ? BigDecimal.ZERO : basePrice);
            entity.setIncludedImageCountSnapshot(includedImages);
            entity.setIncludedVideoCountSnapshot(includedVideos);
            entity.setRequestedImageCount(requestedImages);
            entity.setRequestedVideoCount(requestedVideos);
            entity.setPricingProfileSnapshot(profile);
            entity.setStandardVideoDurationSnapshot(custom ? null : source.getStandardVideoDurationSeconds());
            entity.setAreaPricingEnabledSnapshot(!custom && source.isAreaPricingEnabled());
            entity.setLengthPricingEnabledSnapshot(!custom && source.isLengthPricingEnabled());
            entity.setAltitudePricingEnabledSnapshot(!custom && source.isAltitudePricingEnabled());
            entity.setIncludedAreaHaSnapshot(custom ? BigDecimal.ZERO : source.getIncludedAreaHa());
            entity.setIncludedLengthKmSnapshot(custom ? BigDecimal.ZERO : source.getIncludedLengthKm());
            entity.setIncludedAltitudeMSnapshot(custom ? BigDecimal.ZERO : source.getIncludedAltitudeM());
            if (explicitMedia || (!explicitEvidence && requestedImages + requestedVideos > 0)) {
                // Evidence policy V3: acceptance needs the requested images AND the requested videos.
                entity.setEvidencePolicyVersion(3);
                entity.setMinimumImageCount(requestedImages);
                entity.setMinimumVideoCount(requestedVideos);
                entity.setMinimumEvidenceCount(requestedImages + requestedVideos);
                entity.setRequiredEvidenceType(requestedVideos == 0 ? RequiredEvidenceType.IMAGE
                        : requestedImages == 0 ? RequiredEvidenceType.VIDEO : RequiredEvidenceType.IMAGE_OR_VIDEO);
            } else {
                int minimumEvidenceCount = item.getMinimumEvidenceCount() == null ? 1 : item.getMinimumEvidenceCount();
                RequiredEvidenceType requiredEvidenceType = item.getRequiredEvidenceType() == null
                        ? RequiredEvidenceType.IMAGE_OR_VIDEO : item.getRequiredEvidenceType();
                if (minimumEvidenceCount < 1 || minimumEvidenceCount > 50) {
                    throw invalid("Checklist item '" + (item.getContentOverride() == null ? "selected requirement" : item.getContentOverride()) + "' minimumEvidenceCount must be between 1 and 50");
                }
                entity.setEvidencePolicyVersion(2);
                entity.setMinimumEvidenceCount(minimumEvidenceCount);
                entity.setRequiredEvidenceType(requiredEvidenceType);
            }
            entity.setOrder(order);
            entity.setSourceChecklist(source);
            entity.setDifficultySnapshot(source == null || source.getDifficulty() == null
                    ? ChecklistDifficulty.BASIC : source.getDifficulty());
            entity.setSourceType(custom ? OrderChecklistSourceType.CUSTOMER_CUSTOM
                    : packageChecklist ? OrderChecklistSourceType.SERVICE_TEMPLATE : OrderChecklistSourceType.CUSTOMER_SELECTED);
            entity.setReviewStatus(packageChecklist ? ChecklistReviewStatus.INCLUDED : ChecklistReviewStatus.PENDING);
            String content = item.getContentOverride();
            if (content == null && source != null) {
                content = source.getContent();
            }
            entity.setContent(ChecklistContentNormalizer.content(content));
            if (!contents.add(entity.getContent().toLowerCase(Locale.ROOT))) throw invalid("Duplicate checklist content");
            entity.setDisplayOrder(entities.size());
            // Fail fast: a selection the policy cannot price must not become a snapshot.
            if (policy != null && !custom) priceCalculator.price(policy, new ChecklistPriceCalculator.Input(
                    source == null ? null : source.getId(), entity.getContent(), entity.getDifficultySnapshot(), custom,
                    entity.getBasePriceSnapshot(), custom ? null : includedImages, custom ? null : includedVideos,
                    requestedImages, requestedVideos, profile, entity.getStandardVideoDurationSnapshot(), false));
            entities.add(entity);
        }
        if (entities.size() > 100) throw invalid("At most 100 checklist items are allowed");
        // Checklist-priced service: the checklists are the whole default price, so the legacy catalog price must not be added.
        if (policy != null && ServicePriceResolver.CHECKLISTS.equals(servicePrices.resolveOrLegacy(service).source()))
            order.setServiceBasePriceSnapshot(BigDecimal.ZERO);
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
