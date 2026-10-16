package com.ondemandmonitoring.pricing.service;

import com.ondemandmonitoring.common.exception.ApiException;
import com.ondemandmonitoring.common.exception.ErrorCode;
import com.ondemandmonitoring.order.domain.Order;
import com.ondemandmonitoring.order.domain.OrderChecklistItem;
import com.ondemandmonitoring.order.domain.OrderDeliverable;
import com.ondemandmonitoring.order.enums.*;
import com.ondemandmonitoring.pricing.domain.*;
import com.ondemandmonitoring.pricing.dto.*;
import com.ondemandmonitoring.pricing.enums.*;
import com.ondemandmonitoring.pricing.repository.*;
import java.math.*;
import java.time.Instant;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.locationtech.jts.geom.Coordinate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RuleBasedPricingEngine implements PricingEngine {
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(0);
    private final PricingPolicyRepository policies;
    private final CustomWorkAssessmentRepository assessments;

    @Override
    @Transactional(readOnly = true)
    public PricingBreakdown calculate(Order order, List<OrderChecklistItem> checklistItems) {
        PricingPolicy policy = policies.findEffectiveActive(Instant.now()).stream().findFirst()
                .orElseThrow(() -> new ApiException(ErrorCode.PRICING_POLICY_NOT_FOUND));
        List<PricingBreakdownItem> result = new ArrayList<>();
        BigDecimal base = money(order.getServiceBasePriceSnapshot() != null
                ? order.getServiceBasePriceSnapshot() : order.getService().getBasePrice());
        add(result, PricingCategory.PACKAGE, order.getService().getName(), BigDecimal.ONE, base, base);

        boolean custom = checklistItems.stream().anyMatch(i -> i.getSourceType() == OrderChecklistSourceType.CUSTOMER_CUSTOM
                && i.getReviewStatus() != ChecklistReviewStatus.REJECTED && i.getReviewStatus() != ChecklistReviewStatus.DUPLICATE);
        Optional<CustomWorkAssessment> assessment = assessments.findByOrderId(order.getId());
        if (custom) keyed(policy, PricingCategory.RESOURCE, "CUSTOM_BASE").ifPresent(r -> addFixed(result, r, BigDecimal.ONE));

        BigDecimal distanceKm = assessment.map(CustomWorkAssessment::getEstimatedDistanceKm).filter(Objects::nonNull)
                .orElseGet(() -> decimal(order.getEstimatedLengthM()).divide(new BigDecimal("1000"), 4, RoundingMode.HALF_UP));
        ranged(policy, PricingCategory.DISTANCE, distanceKm).ifPresent(rule -> applyRule(result, rule, distanceKm, base));

        BigDecimal areaHa = assessment.map(CustomWorkAssessment::getEstimatedAreaHa).filter(Objects::nonNull)
                .orElseGet(() -> polygonHectares(order));
        ranged(policy, PricingCategory.AREA, areaHa).ifPresent(rule -> applyRule(result, rule, areaHa, base));

        for (OrderChecklistItem item : checklistItems) {
            if (item.getReviewStatus() == ChecklistReviewStatus.REJECTED || item.getReviewStatus() == ChecklistReviewStatus.DUPLICATE) continue;
            String key = Optional.ofNullable(item.getDifficultySnapshot()).orElse(ChecklistDifficulty.BASIC).name();
            keyed(policy, PricingCategory.CHECKLIST, key).ifPresent(rule -> addFixed(result, rule, BigDecimal.ONE));
        }
        if (order.getResultFormats() != null) for (OrderResultFormat format : order.getResultFormats()) {
            BigDecimal requestedQuantity = requestedOutputQuantity(order, format);
            keyed(policy, PricingCategory.OUTPUT, format.name()).ifPresent(rule -> applyRule(result, rule, requestedQuantity, base));
        }
        BigDecimal highResolutionImages = requestedQualityQuantity(order, "IMAGE", "4K");
        if (highResolutionImages.signum() > 0)
            keyed(policy, PricingCategory.OUTPUT, "HIGH_RESOLUTION_IMAGE").ifPresent(rule -> applyRule(result, rule, highResolutionImages, base));
        BigDecimal highResolutionVideo = requestedQualityQuantity(order, "VIDEO", "4K");
        if (highResolutionVideo.signum() > 0)
            keyed(policy, PricingCategory.OUTPUT, "4K_VIDEO").ifPresent(rule -> applyRule(result, rule, highResolutionVideo, base));
        assessment.ifPresent(a -> {
            resource(result, policy, "PILOT", a.getPilotCount());
            resource(result, policy, "DRONE", a.getDroneCount());
            resource(result, policy, "BATTERY", a.getBatteryUnits());
            if (a.isThermalRequired()) keyed(policy, PricingCategory.OUTPUT, "THERMAL").ifPresent(r -> addFixed(result, r, BigDecimal.ONE));
            if (a.isMultispectralRequired()) keyed(policy, PricingCategory.OUTPUT, "MULTISPECTRAL").ifPresent(r -> addFixed(result, r, BigDecimal.ONE));
            if (a.isAiReportRequired()) keyed(policy, PricingCategory.OUTPUT, "AI_REPORT").ifPresent(r -> addFixed(result, r, BigDecimal.ONE));
        });
        keyed(policy, PricingCategory.CONDITION, Optional.ofNullable(order.getPriority()).orElse(OrderPriority.NORMAL).name())
                .ifPresent(rule -> applyRule(result, rule, BigDecimal.ONE, subtotal(result)));
        if (Boolean.TRUE.equals(order.getPermitRequired()) || order.getPermitStatus() == OrderPermitStatus.NEED_SUPPORT)
            keyed(policy, PricingCategory.PERMIT, "REQUIRED").ifPresent(rule -> addFixed(result, rule, BigDecimal.ONE));
        TerrainType terrain = assessment.map(CustomWorkAssessment::getTerrain).orElse(TerrainType.FLAT);
        keyed(policy, PricingCategory.TERRAIN, terrain.name()).ifPresent(rule -> applyRule(result, rule, BigDecimal.ONE, subtotal(result)));
        for (PricingRule discount : enabled(policy, PricingCategory.DISCOUNT)) applyRule(result, discount, BigDecimal.ONE, subtotal(result));

        BigDecimal total = subtotal(result);
        if (total.signum() < 0) throw new ApiException(ErrorCode.PRICING_CALCULATION_FAILED);
        return new PricingBreakdown(policy.getId(), policy.getPolicyVersion(), policy.getCurrency(), total,
                custom, !custom || assessment.map(CustomWorkAssessment::isAssessmentComplete).orElse(false), List.copyOf(result));
    }

    private void resource(List<PricingBreakdownItem> out, PricingPolicy policy, String key, Integer count) {
        if (count != null && count > 0) keyed(policy, PricingCategory.RESOURCE, key)
                .ifPresent(rule -> addFixed(out, rule, BigDecimal.valueOf(count)));
    }
    private void applyRule(List<PricingBreakdownItem> out, PricingRule rule, BigDecimal value, BigDecimal base) {
        if (rule.getCalculationType() == PricingCalculationType.MANUAL_QUOTE)
            throw new ApiException(ErrorCode.CUSTOM_WORK_PRICING_INCOMPLETE, rule.getDescription());
        BigDecimal quantity = BigDecimal.ONE;
        BigDecimal amount;
        switch (rule.getCalculationType()) {
            case FIXED, ACTUAL_COST, RESOURCE_BASED -> amount = nz(rule.getFixedAmount());
            case PERCENT_OF_BASE -> amount = base.multiply(nz(rule.getPercentage())).divide(new BigDecimal("100"), 0, RoundingMode.HALF_UP);
            case PER_UNIT -> { quantity = value; amount = nz(rule.getFixedAmount()).multiply(value); }
            case PER_BLOCK, TIERED_BLOCK -> {
                BigDecimal chargeable = value.subtract(nz(rule.getMinValue())).max(BigDecimal.ZERO);
                quantity = chargeable.divide(positive(rule.getUnitSize()), 0, RoundingMode.CEILING);
                amount = nz(rule.getFixedAmount()).multiply(quantity);
                if (rule.getCalculationType() == PricingCalculationType.TIERED_BLOCK)
                    amount = amount.add(nz(rule.getBaseAmount()));
            }
            case MULTIPLIER -> amount = base.multiply(nz(rule.getMultiplier()).subtract(BigDecimal.ONE)).setScale(0, RoundingMode.HALF_UP);
            default -> throw new ApiException(ErrorCode.PRICING_CALCULATION_FAILED);
        }
        if (rule.getCategory() == PricingCategory.DISCOUNT && amount.signum() > 0) amount = amount.negate();
        BigDecimal unit = quantity.signum() == 0 ? ZERO : amount.divide(quantity, 0, RoundingMode.HALF_UP);
        add(out, rule.getCategory(), rule.getDescription(), quantity, unit, money(amount));
    }
    private void addFixed(List<PricingBreakdownItem> out, PricingRule rule, BigDecimal quantity) {
        BigDecimal unit = nz(rule.getFixedAmount());
        add(out, rule.getCategory(), rule.getDescription(), quantity, unit, money(unit.multiply(quantity)));
    }
    private Optional<PricingRule> ranged(PricingPolicy policy, PricingCategory category, BigDecimal value) {
        return enabled(policy, category).stream().filter(r -> (r.getMinValue() == null || value.compareTo(r.getMinValue()) > 0)
                && (r.getMaxValue() == null || value.compareTo(r.getMaxValue()) <= 0)).findFirst();
    }
    private Optional<PricingRule> keyed(PricingPolicy policy, PricingCategory category, String key) {
        return enabled(policy, category).stream().filter(r -> r.getRuleKey().equalsIgnoreCase(key)).findFirst();
    }
    private List<PricingRule> enabled(PricingPolicy policy, PricingCategory category) {
        return policy.getRules().stream().filter(PricingRule::isEnabled).filter(r -> r.getCategory() == category).toList();
    }
    private BigDecimal polygonHectares(Order order) {
        if (order.getTargetArea() == null) return BigDecimal.ZERO;
        Coordinate[] points = order.getTargetArea().getExteriorRing().getCoordinates();
        if (points.length < 4) return BigDecimal.ZERO;
        double lat0 = Arrays.stream(points).mapToDouble(c -> c.y).average().orElse(0);
        double sx = 111_320d * Math.cos(Math.toRadians(lat0)), sy = 110_540d, twiceArea = 0;
        for (int i = 0; i < points.length - 1; i++) twiceArea += points[i].x * sx * points[i + 1].y * sy - points[i + 1].x * sx * points[i].y * sy;
        return BigDecimal.valueOf(Math.abs(twiceArea) / 2d / 10_000d).setScale(3, RoundingMode.HALF_UP);
    }
    private BigDecimal requestedOutputQuantity(Order order, OrderResultFormat format) {
        String mediaType = format == OrderResultFormat.PHOTO ? "IMAGE" : format == OrderResultFormat.VIDEO ? "VIDEO" : null;
        if (mediaType == null) return BigDecimal.ONE;
        BigDecimal quantity = order.getDeliverables().stream().map(OrderDeliverable::getRequirement)
                .filter(Objects::nonNull).filter(map -> mediaType.equalsIgnoreCase(String.valueOf(map.get("mediaType"))))
                .map(map -> number(map.get("quantity"))).reduce(BigDecimal.ZERO, BigDecimal::add);
        return quantity.signum() > 0 ? quantity : BigDecimal.ONE;
    }
    private BigDecimal requestedQualityQuantity(Order order, String mediaType, String resolution) {
        return order.getDeliverables().stream().map(OrderDeliverable::getRequirement).filter(Objects::nonNull)
                .filter(map -> mediaType.equalsIgnoreCase(String.valueOf(map.get("mediaType"))))
                .filter(map -> resolution.equalsIgnoreCase(String.valueOf(map.get("resolution"))))
                .map(map -> number(map.get("quantity"))).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
    private BigDecimal number(Object value) {
        if (value instanceof Number number) return new BigDecimal(number.toString());
        try { return value == null ? BigDecimal.ZERO : new BigDecimal(value.toString()); }
        catch (NumberFormatException ignored) { return BigDecimal.ZERO; }
    }
    private BigDecimal subtotal(List<PricingBreakdownItem> items) { return items.stream().map(PricingBreakdownItem::amount).reduce(ZERO, BigDecimal::add); }
    private void add(List<PricingBreakdownItem> out, PricingCategory c, String d, BigDecimal q, BigDecimal u, BigDecimal a) { if (a.signum() != 0 || c == PricingCategory.PACKAGE) out.add(new PricingBreakdownItem(c, d, q, u, a)); }
    private BigDecimal decimal(Double value) { return value == null ? BigDecimal.ZERO : BigDecimal.valueOf(value); }
    private BigDecimal nz(BigDecimal value) { return value == null ? BigDecimal.ZERO : value; }
    private BigDecimal positive(BigDecimal value) { return value == null || value.signum() <= 0 ? BigDecimal.ONE : value; }
    private BigDecimal money(BigDecimal value) { return nz(value).setScale(0, RoundingMode.HALF_UP); }
}
