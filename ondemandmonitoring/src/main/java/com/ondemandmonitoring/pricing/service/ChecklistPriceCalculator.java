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
    public static final String CUSTOM_PROFILE = "CUSTOM";
    public static final int MAX_MEDIA_COUNT = 500;

    /**
     * @param catalogBasePrice price of the checklist work; ignored for custom checklists (priced by the CUSTOM profile).
     * @param includedImages   allowance; for custom checklists {@code null} means the CUSTOM profile allowance.
     * @param requestedImages  {@code null} means "exactly the included allowance".
     */
    public record Input(String checklistId, String content, ChecklistDifficulty difficulty, boolean custom,
            BigDecimal catalogBasePrice, Integer includedImages, Integer includedVideos,
            Integer requestedImages, Integer requestedVideos, String pricingProfile, Integer standardVideoDurationSeconds,
            boolean includeDifficulty) {}

    public ChecklistPriceBreakdown price(PricingPolicy policy, Input in) {
        String profile = in.custom() ? CUSTOM_PROFILE : normalizeProfile(in.pricingProfile());
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

        BigDecimal subtotal = lines.stream().map(Line::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new ChecklistPriceBreakdown(in.checklistId(), in.content(), profile, in.custom(), includedImages, includedVideos,
                requestedImages, requestedVideos, extraImages, extraVideos, in.standardVideoDurationSeconds(), List.copyOf(lines), subtotal);
    }

    /** True when the policy itself prices checklists outside the catalog (a CUSTOM profile CHECKLIST_BASE rule). */
    public boolean hasCustomRule(PricingPolicy policy) {
        return policy != null && !rules(policy, PricingCategory.MEDIA, CUSTOM_PROFILE, MediaPricingComponent.CHECKLIST_BASE).isEmpty();
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
        return new ChecklistPriceBreakdown(null, content, CUSTOM_PROFILE, true, 0, 0, requestedImages == null ? 0 : requestedImages,
                requestedVideos == null ? 0 : requestedVideos, 0, 0, null, List.copyOf(lines), amount, quotedPrice == null);
    }

    /** Allowance a custom checklist gets from policy; used when the order snapshot is captured. */
    public int customAllowance(PricingPolicy policy, MediaPricingComponent component) {
        return allowance(policy, CUSTOM_PROFILE, component, null);
    }

    private BigDecimal customBase(PricingPolicy policy, String profile) {
        Optional<PricingRule> rule = rules(policy, PricingCategory.MEDIA, profile, MediaPricingComponent.CHECKLIST_BASE).stream().findFirst();
        if (rule.isPresent()) return money(rule.get().getFixedAmount());
        // No CUSTOM rule: callers route such checklists to managerQuoted(); reaching here is a programming error.
        throw new ApiException(ErrorCode.CUSTOM_CHECKLIST_PRICING_NOT_CONFIGURED,
                "Custom checklists have no policy price: the Manager must quote them.");
    }

    private int allowance(PricingPolicy policy, String profile, MediaPricingComponent component, Integer configured) {
        if (configured != null) {
            if (configured < 0 || configured > MAX_MEDIA_COUNT) throw new ApiException(ErrorCode.PRICING_CALCULATION_FAILED);
            return configured;
        }
        if (!CUSTOM_PROFILE.equals(profile)) return 0;
        return rules(policy, PricingCategory.MEDIA, profile, component).stream().findFirst()
                .map(PricingRule::getIncludedQuantity).orElse(0);
    }

    private Optional<Line> includedLine(PricingPolicy policy, String profile, MediaPricingComponent component, int allowance, String noun) {
        if (allowance <= 0) return Optional.empty();
        return rules(policy, PricingCategory.MEDIA, profile, component).stream().findFirst().flatMap(rule -> {
            BigDecimal amount = money(rule.getFixedAmount());
            if (amount.signum() == 0) return Optional.empty();
            return Optional.of(new Line(component.name(), PricingCategory.MEDIA, "Đã bao gồm " + allowance + " " + noun,
                    BigDecimal.ONE, amount, amount, null, null, null, null));
        });
    }

    private void tierLines(PricingPolicy policy, String profile, MediaPricingComponent component, int extra, String noun, List<Line> out) {
        if (extra <= 0) return;
        List<PricingRule> tiers = rules(policy, PricingCategory.MEDIA, profile, component).stream()
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

    private List<PricingRule> rules(PricingPolicy policy, PricingCategory category, String profile, MediaPricingComponent component) {
        return policy.getRules().stream().filter(PricingRule::isEnabled).filter(r -> r.getCategory() == category)
                .filter(r -> profile == null || profile.equalsIgnoreCase(r.getPricingProfile()))
                .filter(r -> component == null || r.getMediaComponent() == component).toList();
    }

    public static String normalizeProfile(String profile) {
        return profile == null || profile.isBlank() ? DEFAULT_PROFILE : profile.trim().toUpperCase(Locale.ROOT);
    }
    private static BigDecimal nz(BigDecimal v) { return v == null ? BigDecimal.ZERO : v; }
    private static BigDecimal money(BigDecimal v) { return nz(v).setScale(0, RoundingMode.HALF_UP); }
}
