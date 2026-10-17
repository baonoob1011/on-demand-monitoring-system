package com.ondemandmonitoring.pricing.service;

import com.ondemandmonitoring.checklist.domain.ChecklistDefinition;
import com.ondemandmonitoring.checklist.domain.ServiceChecklist;
import com.ondemandmonitoring.checklist.repository.ChecklistDefinitionRepository;
import com.ondemandmonitoring.checklist.repository.ServiceChecklistRepository;
import com.ondemandmonitoring.checklist.util.ChecklistContentNormalizer;
import com.ondemandmonitoring.common.exception.ApiException;
import com.ondemandmonitoring.common.exception.ErrorCode;
import com.ondemandmonitoring.pricing.config.PricingPolicyInitializer;
import com.ondemandmonitoring.pricing.domain.PricingPolicy;
import com.ondemandmonitoring.pricing.enums.PricingCategory;
import com.ondemandmonitoring.pricing.dto.ChecklistPriceBreakdown;
import com.ondemandmonitoring.pricing.dto.ChecklistPricingPreviewRequests;
import com.ondemandmonitoring.pricing.dto.ServicePricePreviewResponse;
import com.ondemandmonitoring.pricing.enums.PricingPolicyStatus;
import com.ondemandmonitoring.pricing.repository.PricingPolicyRepository;
import com.ondemandmonitoring.service.domain.Service;
import com.ondemandmonitoring.service.repository.ServiceRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-only price previews. They run the same {@link ChecklistPriceCalculator} the order snapshot and the Manager quote use,
 * so an Admin preview, a customer estimate and the later quote cannot drift apart.
 */
@org.springframework.stereotype.Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChecklistPricingPreviewService {
    private final ServiceRepository services;
    private final ChecklistDefinitionRepository definitions;
    private final ServiceChecklistRepository assignments;
    private final PricingPolicyRepository policies;
    private final ChecklistPriceCalculator calculator;
    private final ServicePriceResolver resolver;

    /** Default price of a service: package fee + the stored default checklists at their included allowance. */
    public ServicePricePreviewResponse defaultPrice(String serviceId) {
        return adminPreview(new ChecklistPricingPreviewRequests.Admin(serviceId, null, null));
    }

    public ServicePricePreviewResponse adminPreview(ChecklistPricingPreviewRequests.Admin request) {
        Service service = service(request.serviceId());
        PricingPolicy policy = request.policyId() == null ? activePolicy() : policies.findById(request.policyId())
                .orElseThrow(() -> new ApiException(ErrorCode.PRICING_POLICY_NOT_FOUND));
        Map<String, ServiceChecklist> stored = new LinkedHashMap<>();
        for (ServiceChecklist row : assignments.findAllByServiceIdOrderByDisplayOrderAscIdAsc(service.getId()))
            stored.put(row.getChecklist().getId(), row);

        List<ChecklistPriceBreakdown> priced = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        var context = new ChecklistPriceCalculator.RequestContext(
                request.areaHa(), request.lengthKm(), request.altitudeM());
        if (request.items() == null) {
            for (ServiceChecklist row : stored.values()) {
                if (!Boolean.TRUE.equals(row.getChecklist().getIsActive())) continue;
                priced.add(priceOrWarn(policy, withContext(
                        input(row.getChecklist(), row, null, null, null, null, null, null), context), warnings));
            }
        } else {
            Set<String> seen = new HashSet<>();
            for (var item : request.items()) {
                if (!seen.add(item.checklistId())) throw new ApiException(ErrorCode.INVALID_REQUEST, "Duplicate checklist in preview");
                ChecklistDefinition definition = stored.containsKey(item.checklistId()) ? stored.get(item.checklistId()).getChecklist()
                        : definitions.findById(item.checklistId()).orElseThrow(() -> new ApiException(ErrorCode.CHECKLIST_NOT_FOUND));
                priced.add(priceOrWarn(policy, withContext(input(definition, stored.get(item.checklistId()),
                        item.basePrice(), item.includedImageCount(), item.includedVideoCount(), item.pricingProfile(),
                        item.requestedImageCount(), item.requestedVideoCount()), context), warnings));
            }
        }
        BigDecimal subtotal = priced.stream().map(ChecklistPriceBreakdown::subtotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        var resolved = ServicePriceResolver.of(subtotal);
        return response(service, policy, resolved, priced, warnings, List.of());
    }

    /** Customer estimate. Only checklist choices and quantities are read from the client; every price comes from storage. */
    public ServicePricePreviewResponse customerQuote(ChecklistPricingPreviewRequests.Customer request) {
        Service service = service(request.serviceId());
        if (!Boolean.TRUE.equals(service.getIsActive())) throw new ApiException(ErrorCode.SERVICE_INACTIVE);
        PricingPolicy policy = activePolicy();
        Map<String, ServiceChecklist> template = new LinkedHashMap<>();
        for (ServiceChecklist row : assignments.findAllByServiceIdAndChecklistIsActiveTrueOrderByDisplayOrderAscIdAsc(service.getId()))
            template.put(row.getChecklist().getId(), row);

        List<ChecklistPriceBreakdown> priced = new ArrayList<>();
        var context = new ChecklistPriceCalculator.RequestContext(request.areaHa(), request.lengthKm(), request.altitudeM());
        if (request.items() == null) {
            for (ServiceChecklist row : template.values())
                priced.add(calculator.price(policy, withContext(input(row.getChecklist(), row, null, null, null, null, null, null), context)));
        } else {
            Set<String> seen = new HashSet<>();
            Set<String> contents = new HashSet<>();
            for (var item : request.items()) {
                if (item.sourceChecklistId() != null) {
                    ServiceChecklist row = template.get(item.sourceChecklistId());
                    if (!seen.add(item.sourceChecklistId())) throw new ApiException(ErrorCode.INVALID_REQUEST, "Duplicate source checklist");
                    ChecklistDefinition definition = row == null
                            ? definitions.findById(item.sourceChecklistId())
                                    .filter(candidate -> Boolean.TRUE.equals(candidate.getIsActive()))
                                    .orElseThrow(() -> new ApiException(ErrorCode.CHECKLIST_NOT_FOUND))
                            : row.getChecklist();
                    ChecklistPriceBreakdown calculated = calculator.price(policy, withContext(input(definition, row, null, null, null, null,
                            item.requestedImageCount(), item.requestedVideoCount()), context));
                    priced.add(row == null ? customerHidden(calculated) : calculated);
                } else {
                    if (item.content() == null || item.content().isBlank())
                        throw new ApiException(ErrorCode.INVALID_REQUEST, "A custom checklist needs content");
                    String content = ChecklistContentNormalizer.content(item.content());
                    if (!contents.add(content.toLowerCase(Locale.ROOT))) throw new ApiException(ErrorCode.INVALID_REQUEST, "Duplicate checklist content");
                    ChecklistPriceBreakdown calculated = calculator.hasCustomRule(policy)
                            ? calculator.price(policy, new ChecklistPriceCalculator.Input(null, content, null, true, null, null, null,
                                    item.requestedImageCount(), item.requestedVideoCount(), null, null, true))
                            : calculator.managerQuoted(content, null, item.requestedImageCount(), item.requestedVideoCount());
                    priced.add(customerHidden(calculated));
                }
            }
            template.values().stream().filter(ServiceChecklist::isRequired)
                    .filter(row -> !seen.contains(row.getChecklist().getId())).findAny()
                    .ifPresent(row -> { throw new ApiException(ErrorCode.ORDER_CHECKLIST_REQUIRED_MISSING); });
        }
        // The price source follows the service's default checklist set, exactly as at order creation.
        List<ServicePricePreviewResponse.AddOn> addOns = new ArrayList<>();
        if (Boolean.TRUE.equals(request.aiAnalysisRequested())) {
            var rule = policy.getRules().stream().filter(r -> r.isEnabled() && r.getCategory() == PricingCategory.OUTPUT
                    && PricingPolicyInitializer.AI_IMAGE_ANALYSIS_KEY.equalsIgnoreCase(r.getRuleKey())).findFirst()
                    .orElseThrow(() -> new ApiException(ErrorCode.PRICING_RULE_NOT_FOUND,
                            "AI image analysis is not priced in the active pricing policy"));
            addOns.add(new ServicePricePreviewResponse.AddOn(rule.getRuleKey(), rule.getDescription(),
                    whole(rule.getFixedAmount())));
        }
        return response(service, policy, resolver.resolve(service, policy), priced, List.of(), addOns);
    }

    /** Public customer previews disclose neither the internal amount nor its component breakdown. */
    private ChecklistPriceBreakdown customerHidden(ChecklistPriceBreakdown priced) {
        return new ChecklistPriceBreakdown(priced.checklistId(), priced.content(), priced.pricingProfile(), priced.custom(),
                priced.includedImageCount(), priced.includedVideoCount(), priced.requestedImageCount(),
                priced.requestedVideoCount(), priced.extraImageCount(), priced.extraVideoCount(),
                priced.standardVideoDurationSeconds(), List.of(), BigDecimal.ZERO, true);
    }

    private ChecklistPriceCalculator.Input input(ChecklistDefinition d, ServiceChecklist row, BigDecimal base, Integer images,
            Integer videos, String profile, Integer requestedImages, Integer requestedVideos) {
        BigDecimal effectiveBase = base != null ? base : row != null && row.getBasePriceOverride() != null ? row.getBasePriceOverride() : d.getBasePrice();
        Integer effectiveImages = images != null ? images : row != null && row.getIncludedImageCountOverride() != null
                ? row.getIncludedImageCountOverride() : d.getIncludedImageCount();
        Integer effectiveVideos = videos != null ? videos : row != null && row.getIncludedVideoCountOverride() != null
                ? row.getIncludedVideoCountOverride() : d.getIncludedVideoCount();
        String effectiveProfile = profile != null && !profile.isBlank() ? profile : row != null && row.getPricingProfileOverride() != null
                ? row.getPricingProfileOverride() : d.getPricingProfile();
        return new ChecklistPriceCalculator.Input(d.getId(), d.getContent(), d.getDifficulty(), false, effectiveBase, effectiveImages,
                effectiveVideos, requestedImages, requestedVideos, effectiveProfile, d.getStandardVideoDurationSeconds(), true,
                new ChecklistPriceCalculator.PricingDimensions(d.isAreaPricingEnabled(), d.isLengthPricingEnabled(),
                        d.isAltitudePricingEnabled(), d.getIncludedAreaHa(), d.getIncludedLengthKm(), d.getIncludedAltitudeM()),
                ChecklistPriceCalculator.RequestContext.empty());
    }

    private ChecklistPriceBreakdown priceOrWarn(PricingPolicy policy, ChecklistPriceCalculator.Input input, List<String> warnings) {
        try {
            return calculator.price(policy, input);
        } catch (ApiException exception) {
            // An Admin must see which checklist the policy cannot price instead of a silently smaller total.
            warnings.add(input.content() + ": " + (exception.getMessage() == null ? exception.getErrorCode().name() : exception.getMessage()));
            return new ChecklistPriceBreakdown(input.checklistId(), input.content(), ChecklistPriceCalculator.normalizeProfile(input.pricingProfile()),
                    false, 0, 0, 0, 0, 0, 0, input.standardVideoDurationSeconds(), List.of(), BigDecimal.ZERO);
        }
    }

    private ChecklistPriceCalculator.Input withContext(ChecklistPriceCalculator.Input input,
            ChecklistPriceCalculator.RequestContext context) {
        return new ChecklistPriceCalculator.Input(input.checklistId(), input.content(), input.difficulty(), input.custom(),
                input.catalogBasePrice(), input.includedImages(), input.includedVideos(), input.requestedImages(),
                input.requestedVideos(), input.pricingProfile(), input.standardVideoDurationSeconds(), input.includeDifficulty(),
                input.dimensions(), context);
    }

    private ServicePricePreviewResponse response(Service service, PricingPolicy policy, ServicePriceResolver.Resolved resolved,
            List<ChecklistPriceBreakdown> priced, List<String> warnings, List<ServicePricePreviewResponse.AddOn> addOns) {
        BigDecimal subtotal = priced.stream().map(ChecklistPriceBreakdown::subtotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal addOnTotal = addOns.stream().map(ServicePricePreviewResponse.AddOn::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal fee = resolved.packageFee();
        return new ServicePricePreviewResponse(service.getId(), service.getName(), policy.getCurrency(), policy.getId(), policy.getPolicyVersion(),
                policy.getStatus() == PricingPolicyStatus.DRAFT, resolved.source(), fee, List.copyOf(priced), subtotal, fee.add(subtotal).add(addOnTotal),
                warnings.isEmpty(), List.copyOf(warnings), priced.stream().anyMatch(ChecklistPriceBreakdown::quotePending),
                List.copyOf(addOns));
    }

    private PricingPolicy activePolicy() {
        return policies.findEffectiveActive(Instant.now()).stream().findFirst()
                .orElseThrow(() -> new ApiException(ErrorCode.PRICING_POLICY_NOT_FOUND));
    }
    private Service service(String id) {
        return services.findById(id).orElseThrow(() -> new ApiException(ErrorCode.SERVICE_NOT_FOUND));
    }
    private static BigDecimal whole(BigDecimal v) { return v == null ? BigDecimal.ZERO : v.setScale(0, RoundingMode.HALF_UP); }
}
