package com.ondemandmonitoring.pricing.service;

import com.ondemandmonitoring.common.exception.ApiException;
import com.ondemandmonitoring.common.exception.ErrorCode;
import com.ondemandmonitoring.pricing.domain.PricingPolicy;
import com.ondemandmonitoring.pricing.domain.PricingRule;
import com.ondemandmonitoring.pricing.dto.ChecklistPriceBreakdown;
import com.ondemandmonitoring.pricing.dto.ChecklistPriceBreakdown.Line;
import com.ondemandmonitoring.pricing.enums.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * The single source of truth for what one checklist costs. It reads only the stored pricing policy
 * (MEDIA rules per pricing profile) and the checklist configuration handed in, so the Admin preview,
 * the customer quote and the Manager quote cannot disagree.
 *
 * <pre>
 * checklist price = base + difficulty rule + included image price + included video price
 *                 + extra image tiers(max(0, requested - included)) + extra video tiers(...)
 * </pre>
 * A component is billed once; the included allowance is never re-billed through the extra tiers.
 */
@Component
public class ChecklistPriceCalculator {
    public static final String DEFAULT_PROFILE = "DEFAULT";
    /** Pricing profile used for free-text requirements added by a customer. */
    public static final String GENERIC_CUSTOMER_ADDED_PROFILE = "GENERIC_CUSTOMER_ADDED";
    /** Kept only so policies published before the profile rename can still price historical orders. */
    public static final String LEGACY_CUSTOM_PROFILE = "CUSTOM";
    public static final int MAX_MEDIA_COUNT = 500;

    /**
     * @param catalogBasePrice price of the checklist work; ignored for custom checklists (priced by the CUSTOM profile).
     * @param includedImages   allowance; for custom checklists {@code null} means the CUSTOM profile allowance.
     * @param requestedImages  {@code null} means "exactly the included allowance".
     */
    public record PricingDimensions(boolean areaEnabled, boolean lengthEnabled, boolean altitudeEnabled,
            BigDecimal includedAreaHa, BigDecimal includedLengthKm, BigDecimal includedAltitudeM) {
        public static PricingDimensions disabled() {
            return new PricingDimensions(false, false, false, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        }
    }

    public record RequestContext(BigDecimal areaHa, BigDecimal lengthKm, BigDecimal altitudeM) {
        public static RequestContext empty() {
            return new RequestContext(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        }
    }

    public record Input(String checklistId, String content, ChecklistDifficulty difficulty, boolean custom,
            BigDecimal catalogBasePrice, Integer includedImages, Integer includedVideos,
            Integer requestedImages, Integer requestedVideos, String pricingProfile, Integer standardVideoDurationSeconds,
            boolean includeDifficulty, PricingDimensions dimensions, RequestContext context) {
        public Input(String checklistId, String content, ChecklistDifficulty difficulty, boolean custom,
                BigDecimal catalogBasePrice, Integer includedImages, Integer includedVideos,
                Integer requestedImages, Integer requestedVideos, String pricingProfile, Integer standardVideoDurationSeconds,
                boolean includeDifficulty) {
            this(checklistId, content, difficulty, custom, catalogBasePrice, includedImages, includedVideos,
                    requestedImages, requestedVideos, pricingProfile, standardVideoDurationSeconds, includeDifficulty,
                    PricingDimensions.disabled(), RequestContext.empty());
        }
    }

    public ChecklistPriceBreakdown price(PricingPolicy policy, Input in) {
        String profile = in.custom()
                ? normalizeProfile(in.pricingProfile() == null ? GENERIC_CUSTOMER_ADDED_PROFILE : in.pricingProfile())
                : normalizeProfile(in.pricingProfile());
        List<Line> lines = new ArrayList<>();

        BigDecimal base = in.custom() ? customBase(policy, profile) : money(in.catalogBasePrice());
        if (base.signum() < 0) throw new ApiException(ErrorCode.PRICING_CALCULATION_FAILED);
        if (base.signum() > 0) lines.add(new Line(in.custom() ? "CUSTOM_BASE" : "BASE", PricingCategory.CHECKLIST,
                in.custom() ? "Checklist ngoài danh mục" : "Giá cơ bản checklist", BigDecimal.ONE, base, base, null, null, null, null));

        if (in.includeDifficulty()) {
            String key = (in.difficulty() == null ? ChecklistDifficulty.BASIC : in.difficulty()).name();
            rules(policy, PricingCategory.CHECKLIST, null, null).stream().filter(r -> r.getRuleKey().equalsIgnoreCase(key)).findFirst()
                    .ifPresent(rule -> {
                        BigDecimal amount = money(rule.getFixedAmount());
                        if (amount.signum() != 0) lines.add(new Line("DIFFICULTY", PricingCategory.CHECKLIST, rule.getDescription(),
                                BigDecimal.ONE, amount, amount, null, null, null, null));
                    });
        }

        int includedImages = allowance(policy, profile, MediaPricingComponent.INCLUDED_IMAGE, in.includedImages());
        int includedVideos = allowance(policy, profile, MediaPricingComponent.INCLUDED_VIDEO, in.includedVideos());
        int requestedImages = in.requestedImages() == null ? includedImages : in.requestedImages();
        int requestedVideos = in.requestedVideos() == null ? includedVideos : in.requestedVideos();
        if (requestedImages < 0 || requestedVideos < 0 || requestedImages > MAX_MEDIA_COUNT || requestedVideos > MAX_MEDIA_COUNT)
            throw new ApiException(ErrorCode.INVALID_REQUEST, "Requested media counts must be between 0 and " + MAX_MEDIA_COUNT);

        includedLine(policy, profile, MediaPricingComponent.INCLUDED_IMAGE, includedImages, "ảnh").ifPresent(lines::add);
        includedLine(policy, profile, MediaPricingComponent.INCLUDED_VIDEO, includedVideos, "video").ifPresent(lines::add);

        int extraImages = Math.max(0, requestedImages - includedImages);
        int extraVideos = Math.max(0, requestedVideos - includedVideos);
        tierLines(policy, profile, MediaPricingComponent.EXTRA_IMAGE, extraImages, "ảnh bổ sung", lines);
        tierLines(policy, profile, MediaPricingComponent.EXTRA_VIDEO, extraVideos, "video bổ sung", lines);

        PricingDimensions dimensions = in.dimensions() == null ? PricingDimensions.disabled() : in.dimensions();
        RequestContext context = in.context() == null ? RequestContext.empty() : in.context();
        if (dimensions.areaEnabled()) dimensionLines(policy, profile, PricingCategory.AREA, "AREA",
                positive(context.areaHa()).subtract(positive(dimensions.includedAreaHa())).max(BigDecimal.ZERO),
                "diện tích bổ sung", lines);
        if (dimensions.lengthEnabled()) dimensionLines(policy, profile, PricingCategory.DISTANCE, "LENGTH",
                positive(context.lengthKm()).subtract(positive(dimensions.includedLengthKm())).max(BigDecimal.ZERO),
                "chiều dài bổ sung", lines);
        if (dimensions.altitudeEnabled()) dimensionLines(policy, profile, PricingCategory.ALTITUDE, "ALTITUDE",
                positive(context.altitudeM()).subtract(positive(dimensions.includedAltitudeM())).max(BigDecimal.ZERO),
                "độ cao bổ sung", lines);

        BigDecimal subtotal = lines.stream().map(Line::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new ChecklistPriceBreakdown(in.checklistId(), in.content(), profile, in.custom(), includedImages, includedVideos,
                requestedImages, requestedVideos, extraImages, extraVideos, in.standardVideoDurationSeconds(), List.copyOf(lines), subtotal);
    }

    /** True when the policy itself prices checklists outside the catalog (a CUSTOM profile CHECKLIST_BASE rule). */
    public boolean hasCustomRule(PricingPolicy policy) {
        return policy != null && (!rules(policy, PricingCategory.MEDIA, GENERIC_CUSTOMER_ADDED_PROFILE,
                MediaPricingComponent.CHECKLIST_BASE).isEmpty()
                || !rules(policy, PricingCategory.MEDIA, LEGACY_CUSTOM_PROFILE,
                MediaPricingComponent.CHECKLIST_BASE).isEmpty());
    }

    /**
     * A checklist the policy cannot price. Until the Manager quotes it the price is pending (and says so); once quoted the
     * Manager's amount is the whole price of the checklist.
     */
    public ChecklistPriceBreakdown managerQuoted(String content, BigDecimal quotedPrice, Integer requestedImages, Integer requestedVideos) {
        List<Line> lines = new ArrayList<>();
        BigDecimal amount = quotedPrice == null ? BigDecimal.ZERO : money(quotedPrice);
        if (quotedPrice != null) lines.add(new Line("MANAGER_QUOTE", PricingCategory.CHECKLIST, "Giá do Manager báo", BigDecimal.ONE, amount, amount,
                null, null, null, null));
        return new ChecklistPriceBreakdown(null, content, GENERIC_CUSTOMER_ADDED_PROFILE, true, 0, 0, requestedImages == null ? 0 : requestedImages,
                requestedVideos == null ? 0 : requestedVideos, 0, 0, null, List.copyOf(lines), amount, quotedPrice == null);
    }

    /** Allowance a custom checklist gets from policy; used when the order snapshot is captured. */
    public int customAllowance(PricingPolicy policy, MediaPricingComponent component) {
        return allowance(policy, GENERIC_CUSTOMER_ADDED_PROFILE, component, null);
    }

    private BigDecimal customBase(PricingPolicy policy, String profile) {
        Optional<PricingRule> rule = profileRules(policy, PricingCategory.MEDIA, profile,
                MediaPricingComponent.CHECKLIST_BASE).stream().findFirst();
        if (rule.isPresent()) return money(rule.get().getFixedAmount());
        // No CUSTOM rule means policy configuration is incomplete; a Manager never invents the baseline amount.
        throw new ApiException(ErrorCode.CUSTOM_CHECKLIST_PRICING_NOT_CONFIGURED,
                "Customer-added checklist pricing is not configured. An administrator must publish a "
                        + GENERIC_CUSTOMER_ADDED_PROFILE + " checklist base rule.");
    }

    private int allowance(PricingPolicy policy, String profile, MediaPricingComponent component, Integer configured) {
        if (configured != null) {
            if (configured < 0 || configured > MAX_MEDIA_COUNT) throw new ApiException(ErrorCode.PRICING_CALCULATION_FAILED);
            return configured;
        }
        if (!GENERIC_CUSTOMER_ADDED_PROFILE.equals(profile) && !LEGACY_CUSTOM_PROFILE.equals(profile)) return 0;
        return profileRules(policy, PricingCategory.MEDIA, profile, component).stream().findFirst()
                .map(PricingRule::getIncludedQuantity).orElse(0);
    }

    private Optional<Line> includedLine(PricingPolicy policy, String profile, MediaPricingComponent component, int allowance, String noun) {
        if (allowance <= 0) return Optional.empty();
        return profileRules(policy, PricingCategory.MEDIA, profile, component).stream().findFirst().flatMap(rule -> {
            BigDecimal amount = money(rule.getFixedAmount());
            if (amount.signum() == 0) return Optional.empty();
            return Optional.of(new Line(component.name(), PricingCategory.MEDIA, "Đã bao gồm " + allowance + " " + noun,
                    BigDecimal.ONE, amount, amount, null, null, null, null));
        });
    }

    private void tierLines(PricingPolicy policy, String profile, MediaPricingComponent component, int extra, String noun, List<Line> out) {
        if (extra <= 0) return;
        List<PricingRule> tiers = profileRules(policy, PricingCategory.MEDIA, profile, component).stream()
                .sorted(Comparator.comparing((PricingRule r) -> nz(r.getMinValue())).thenComparingInt(PricingRule::getPriority)).toList();
        if (tiers.isEmpty()) throw new ApiException(ErrorCode.MEDIA_PRICING_NOT_CONFIGURED,
                "Cannot price " + extra + " " + noun + ": pricing profile " + profile + " has no price tier for it. "
                        + "An administrator must add a tier starting at 0 for profile " + profile + " in the pricing policy and publish it; "
                        + "alternatively request no more than the included allowance.");
        BigDecimal total = BigDecimal.valueOf(extra);
        BigDecimal covered = BigDecimal.ZERO;
        for (PricingRule tier : tiers) {
            if (covered.compareTo(total) >= 0) break;
            BigDecimal lo = nz(tier.getMinValue());
            BigDecimal hi = tier.getMaxValue();
            // Tiers must be contiguous from 0; a gap would silently make that range free.
            if (lo.compareTo(covered) != 0) break;
            BigDecimal upper = hi == null ? total : hi.min(total);
            BigDecimal quantity = upper.subtract(lo);
            if (quantity.signum() <= 0) break;
            covered = upper;
            BigDecimal size = tier.getUnitSize() == null || tier.getUnitSize().signum() <= 0 ? BigDecimal.ONE : tier.getUnitSize();
            PricingRounding rounding = tier.getRoundingMode() == null ? PricingRounding.CEILING : tier.getRoundingMode();
            RoundingMode mode = switch (rounding) {
                case CEILING -> RoundingMode.CEILING;
                case FLOOR -> RoundingMode.FLOOR;
                case HALF_UP -> RoundingMode.HALF_UP;
            };
            BigDecimal blocks = quantity.divide(size, 0, mode);
            BigDecimal unit = money(tier.getFixedAmount());
            BigDecimal amount = unit.multiply(blocks);
            String range = lo.stripTrailingZeros().toPlainString() + "–" + (hi == null ? "∞" : hi.stripTrailingZeros().toPlainString());
            out.add(new Line(component.name(), PricingCategory.MEDIA,
                    "Phần " + noun + ": " + quantity.stripTrailingZeros().toPlainString() + " (khoảng " + range + ", khối "
                            + size.stripTrailingZeros().toPlainString() + ")",
                    blocks, unit, amount, lo.intValue(), hi == null ? null : hi.intValue(), size.intValue(), rounding.name()));
        }
        if (covered.compareTo(total) < 0)
            throw new ApiException(ErrorCode.MEDIA_PRICING_NOT_CONFIGURED,
                    "Cannot price " + extra + " " + noun + ": the price tiers of profile " + profile + " only cover up to "
                            + covered.stripTrailingZeros().toPlainString() + ". An administrator must extend the tiers (contiguous from 0, "
                            + "last tier open-ended) in the pricing policy and publish it; alternatively request fewer.");
    }

    private void dimensionLines(PricingPolicy policy, String profile, PricingCategory category, String component,
            BigDecimal extra, String noun, List<Line> out) {
        if (extra.signum() <= 0) return;
        List<PricingRule> tiers = policy.getRules().stream().filter(PricingRule::isEnabled)
                .filter(rule -> rule.getCategory() == category)
                .filter(rule -> rule.getPricingProfile() == null || rule.getPricingProfile().isBlank()
                        || profile.equalsIgnoreCase(rule.getPricingProfile()))
                .sorted(Comparator.comparing((PricingRule r) -> nz(r.getMinValue())).thenComparingInt(PricingRule::getPriority))
                .toList();
        if (tiers.isEmpty()) {
            throw new ApiException(ErrorCode.PRICING_RULE_NOT_FOUND,
                    "Cannot price " + noun + " for checklist profile " + profile + ".");
        }

        BigDecimal covered = BigDecimal.ZERO;
        for (PricingRule tier : tiers) {
            if (covered.compareTo(extra) >= 0) break;
            BigDecimal lo = positive(tier.getMinValue());
            BigDecimal hi = tier.getMaxValue();
            if (lo.compareTo(covered) != 0) break;
            BigDecimal upper = hi == null ? extra : hi.min(extra);
            BigDecimal quantity = upper.subtract(lo);
            if (quantity.signum() <= 0) break;

            BigDecimal unitSize = positive(tier.getUnitSize());
            if (unitSize.signum() == 0) unitSize = BigDecimal.ONE;
            PricingRounding rounding = tier.getRoundingMode() == null ? PricingRounding.CEILING : tier.getRoundingMode();
            RoundingMode roundingMode = switch (rounding) {
                case CEILING -> RoundingMode.CEILING;
                case FLOOR -> RoundingMode.FLOOR;
                case HALF_UP -> RoundingMode.HALF_UP;
            };

            BigDecimal billedQuantity;
            BigDecimal unitPrice = money(tier.getFixedAmount());
            BigDecimal amount;
            PricingCalculationType calculation = tier.getCalculationType();
            if (calculation == PricingCalculationType.FIXED) {
                billedQuantity = BigDecimal.ONE;
                amount = unitPrice;
            } else if (calculation == PricingCalculationType.PER_UNIT) {
                billedQuantity = quantity;
                amount = money(unitPrice.multiply(billedQuantity));
            } else if (calculation == PricingCalculationType.PER_BLOCK
                    || calculation == PricingCalculationType.TIERED_BLOCK) {
                billedQuantity = quantity.divide(unitSize, 0, roundingMode);
                amount = money(unitPrice.multiply(billedQuantity));
                if (calculation == PricingCalculationType.TIERED_BLOCK) amount = amount.add(money(tier.getBaseAmount()));
            } else {
                throw new ApiException(ErrorCode.PRICING_CALCULATION_FAILED,
                        "Unsupported " + category + " calculation type: " + calculation);
            }

            out.add(new Line(component, category,
                    tier.getDescription() == null || tier.getDescription().isBlank() ? noun : tier.getDescription(),
                    billedQuantity, unitPrice, amount, lo.intValue(), hi == null ? null : hi.intValue(),
                    unitSize.intValue(), rounding.name()));
            covered = upper;
        }

        if (covered.compareTo(extra) < 0) {
            throw new ApiException(ErrorCode.PRICING_RULE_NOT_FOUND,
                    "Cannot price " + noun + " for checklist profile " + profile
                            + ": rules must be contiguous from 0 and end with an open tier.");
        }
    }

    private List<PricingRule> rules(PricingPolicy policy, PricingCategory category, String profile, MediaPricingComponent component) {
        return policy.getRules().stream().filter(PricingRule::isEnabled).filter(r -> r.getCategory() == category)
                .filter(r -> profile == null || profile.equalsIgnoreCase(r.getPricingProfile()))
                .filter(r -> component == null || r.getMediaComponent() == component).toList();
    }

    private List<PricingRule> profileRules(PricingPolicy policy, PricingCategory category, String profile,
            MediaPricingComponent component) {
        List<PricingRule> configured = rules(policy, category, profile, component);
        if (!configured.isEmpty() || !GENERIC_CUSTOMER_ADDED_PROFILE.equals(profile)) return configured;
        return rules(policy, category, LEGACY_CUSTOM_PROFILE, component);
    }

    public static String normalizeProfile(String profile) {
        return profile == null || profile.isBlank() ? DEFAULT_PROFILE : profile.trim().toUpperCase(Locale.ROOT);
    }
    private static BigDecimal nz(BigDecimal v) { return v == null ? BigDecimal.ZERO : v; }
    private static BigDecimal positive(BigDecimal v) { return nz(v).max(BigDecimal.ZERO); }
    private static BigDecimal money(BigDecimal v) { return nz(v).setScale(0, RoundingMode.HALF_UP); }
}
