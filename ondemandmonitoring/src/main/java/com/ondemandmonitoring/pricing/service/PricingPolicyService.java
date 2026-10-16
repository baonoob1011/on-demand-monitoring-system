package com.ondemandmonitoring.pricing.service;

import com.ondemandmonitoring.common.exception.*;
import com.ondemandmonitoring.pricing.domain.*;
import com.ondemandmonitoring.pricing.dto.*;
import com.ondemandmonitoring.pricing.enums.*;
import java.math.BigDecimal;
import com.ondemandmonitoring.pricing.repository.PricingPolicyRepository;
import java.time.Instant;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @RequiredArgsConstructor
public class PricingPolicyService {
    private final PricingPolicyRepository policies;

    private com.ondemandmonitoring.pricing.service.ServiceDefaultPriceService defaultPrices;

    /** Optional collaborator: keeps the stored service default price current. */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setDefaultPrices(com.ondemandmonitoring.pricing.service.ServiceDefaultPriceService defaultPrices) {
        this.defaultPrices = defaultPrices;
    }

    @Transactional(readOnly = true)
    public List<PricingPolicyResponse> list() { return policies.findAllByOrderByPolicyVersionDesc().stream().map(this::response).toList(); }

    @Transactional
    public PricingPolicyResponse createDraft(PricingPolicyRequest request) {
        int version = policies.findAllByOrderByPolicyVersionDesc().stream().mapToInt(PricingPolicy::getPolicyVersion).max().orElse(0) + 1;
        PricingPolicy policy = new PricingPolicy(); policy.setPolicyVersion(version); policy.setStatus(PricingPolicyStatus.DRAFT);
        apply(policy, request); return response(policies.save(policy));
    }

    @Transactional
    public PricingPolicyResponse updateDraft(String id, PricingPolicyRequest request) {
        PricingPolicy policy = policies.findById(id).orElseThrow(() -> new ApiException(ErrorCode.PRICING_POLICY_NOT_FOUND));
        requireDraft(policy); apply(policy, request); return response(policy);
    }

    @Transactional
    public PricingPolicyResponse publish(String id) {
        PricingPolicy policy = policies.findById(id).orElseThrow(() -> new ApiException(ErrorCode.PRICING_POLICY_NOT_FOUND));
        requireDraft(policy); Instant now = Instant.now();
        for (PricingPolicy active : policies.findAllByStatus(PricingPolicyStatus.ACTIVE)) {
            active.setStatus(PricingPolicyStatus.SUPERSEDED); active.setEffectiveTo(now);
        }
        policy.setStatus(PricingPolicyStatus.ACTIVE);
        if (policy.getEffectiveFrom() == null || policy.getEffectiveFrom().isAfter(now)) policy.setEffectiveFrom(now);
        policies.flush();
        // New included-media prices, tiers and difficulty rules change what every service's default checklists cost.
        if (defaultPrices != null) defaultPrices.refreshAll();
        return response(policy);
    }

    private void apply(PricingPolicy p, PricingPolicyRequest r) {
        if (r.effectiveFrom() != null && r.effectiveTo() != null && !r.effectiveTo().isAfter(r.effectiveFrom()))
            throw new ApiException(ErrorCode.INVALID_REQUEST, "effectiveTo must be after effectiveFrom");
        if (r.highRiskDiscountPercent().compareTo(r.managerMaxDiscountPercent()) > 0)
            throw new ApiException(ErrorCode.INVALID_REQUEST, "High-risk threshold cannot exceed maximum discount");
        p.setName(r.name().trim()); p.setCurrency("VND"); p.setEffectiveFrom(r.effectiveFrom()); p.setEffectiveTo(r.effectiveTo());
        p.setManagerMaxDiscountPercent(r.managerMaxDiscountPercent()); p.setManagerMaxMarkupPercent(r.managerMaxMarkupPercent());
        p.setHighRiskDiscountPercent(r.highRiskDiscountPercent()); p.setMinimumPriceRatio(r.minimumPriceRatio()); p.setMinimumPrice(r.minimumPrice());
        List<PricingRule> rules = new ArrayList<>(); Set<String> keys = new HashSet<>();
        for (PricingRuleRequest rr : r.rules()) {
            if (!keys.add(rr.category() + ":" + rr.ruleKey().toUpperCase(Locale.ROOT)))
                throw new ApiException(ErrorCode.INVALID_REQUEST, "Duplicate pricing rule key");
            PricingRule rule = new PricingRule(); rule.setCategory(rr.category()); rule.setCalculationType(rr.calculationType());
            rule.setRuleKey(rr.ruleKey().trim().toUpperCase(Locale.ROOT)); rule.setDescription(rr.description().trim());
            rule.setMinValue(rr.minValue()); rule.setMaxValue(rr.maxValue()); rule.setFixedAmount(rr.fixedAmount()); rule.setBaseAmount(rr.baseAmount());
            rule.setPercentage(rr.percentage()); rule.setUnitSize(rr.unitSize()); rule.setMultiplier(rr.multiplier());
            rule.setPriority(rr.priority()); rule.setEnabled(rr.enabled());
            rule.setPricingProfile(rr.pricingProfile() == null || rr.pricingProfile().isBlank() ? null
                    : rr.pricingProfile().trim().toUpperCase(Locale.ROOT));
            rule.setMediaComponent(rr.mediaComponent()); rule.setIncludedQuantity(rr.includedQuantity());
            rule.setRoundingMode(rr.roundingMode());
            validateRule(rule); rules.add(rule);
        }
        validateMediaTiers(rules);
        p.replaceRules(rules);
    }
    private static final java.util.regex.Pattern PROFILE = java.util.regex.Pattern.compile("^[A-Z0-9_]{1,60}$");

    private void validateRule(PricingRule r) {
        for (BigDecimal amount : new BigDecimal[] {r.getFixedAmount(), r.getBaseAmount(), r.getMinValue(), r.getMaxValue(), r.getUnitSize()})
            if (amount != null && amount.signum() < 0) throw invalid("Pricing amounts, ranges and block sizes cannot be negative");
        for (BigDecimal money : new BigDecimal[] {r.getFixedAmount(), r.getBaseAmount()})
            if (money != null && money.stripTrailingZeros().scale() > 0) throw invalid("Prices are whole VND amounts");
        if (r.getMinValue() != null && r.getMaxValue() != null && r.getMaxValue().compareTo(r.getMinValue()) <= 0)
            throw invalid("Rule '" + r.getRuleKey() + "' needs maxValue greater than minValue");
        if (r.getCategory() != PricingCategory.MEDIA) {
            if (r.getPricingProfile() != null || r.getMediaComponent() != null || r.getIncludedQuantity() != null || r.getRoundingMode() != null)
                throw invalid("Profile, media component, allowance and rounding apply to MEDIA rules only");
            return;
        }
        if (r.getPricingProfile() == null || !PROFILE.matcher(r.getPricingProfile()).matches())
            throw invalid("MEDIA rule '" + r.getRuleKey() + "' needs a pricing profile of letters, digits and underscore");
        if (r.getMediaComponent() == null) throw invalid("MEDIA rule '" + r.getRuleKey() + "' needs a media component");
        if (r.getFixedAmount() == null) throw invalid("MEDIA rule '" + r.getRuleKey() + "' needs a price (fixedAmount)");
        boolean tier = r.getMediaComponent() == MediaPricingComponent.EXTRA_IMAGE || r.getMediaComponent() == MediaPricingComponent.EXTRA_VIDEO;
        if (tier) {
            if (r.getUnitSize() == null || r.getUnitSize().signum() <= 0 || r.getUnitSize().stripTrailingZeros().scale() > 0)
                throw invalid("Extra-media tier '" + r.getRuleKey() + "' needs a whole block size of at least 1");
            if (r.getMinValue() == null) r.setMinValue(BigDecimal.ZERO);
            if (r.getRoundingMode() == null) r.setRoundingMode(PricingRounding.CEILING);
            r.setCalculationType(PricingCalculationType.PER_BLOCK);
            if (r.getIncludedQuantity() != null) throw invalid("Allowance applies to included-media rules only");
        } else {
            if (r.getMinValue() != null || r.getMaxValue() != null || r.getUnitSize() != null || r.getRoundingMode() != null)
                throw invalid("Only extra-media tiers have ranges, block sizes and rounding");
            r.setCalculationType(PricingCalculationType.FIXED);
            boolean included = r.getMediaComponent() == MediaPricingComponent.INCLUDED_IMAGE || r.getMediaComponent() == MediaPricingComponent.INCLUDED_VIDEO;
            if (r.getIncludedQuantity() != null && !(included && ChecklistPriceCalculator.CUSTOM_PROFILE.equals(r.getPricingProfile())))
                throw invalid("An allowance is configured on the checklist, except for the CUSTOM profile");
        }
    }

    /** Tiers of one profile/component must start at 0, be contiguous and never overlap; only the last may be open-ended. */
    private void validateMediaTiers(List<PricingRule> rules) {
        Map<String, List<PricingRule>> groups = new HashMap<>();
        for (PricingRule r : rules) {
            if (r.getCategory() != PricingCategory.MEDIA) continue;
            if (r.isEnabled() && (r.getMediaComponent() == MediaPricingComponent.EXTRA_IMAGE || r.getMediaComponent() == MediaPricingComponent.EXTRA_VIDEO))
                groups.computeIfAbsent(r.getPricingProfile() + ":" + r.getMediaComponent(), k -> new ArrayList<>()).add(r);
        }
        for (var entry : groups.entrySet()) {
            List<PricingRule> tiers = entry.getValue().stream().sorted(Comparator.comparing(PricingRule::getMinValue)).toList();
            BigDecimal cursor = BigDecimal.ZERO;
            for (int i = 0; i < tiers.size(); i++) {
                PricingRule tier = tiers.get(i);
                if (tier.getMinValue().compareTo(cursor) < 0) throw invalid("Price tiers overlap for " + entry.getKey());
                if (tier.getMinValue().compareTo(cursor) > 0) throw invalid("Price tiers for " + entry.getKey() + " leave a gap before " + tier.getMinValue().stripTrailingZeros().toPlainString());
                if (tier.getMaxValue() == null) {
                    if (i != tiers.size() - 1) throw invalid("Only the last price tier of " + entry.getKey() + " may be open-ended");
                } else cursor = tier.getMaxValue();
            }
        }
    }
    private ApiException invalid(String message) { return new ApiException(ErrorCode.INVALID_REQUEST, message); }

    private void requireDraft(PricingPolicy p) { if (p.getStatus() != PricingPolicyStatus.DRAFT) throw new ApiException(ErrorCode.INVALID_REQUEST, "Published pricing policies are immutable"); }
    private PricingPolicyResponse response(PricingPolicy p) { return new PricingPolicyResponse(p.getId(), p.getName(), p.getPolicyVersion(), p.getStatus(), p.getCurrency(), p.getEffectiveFrom(), p.getEffectiveTo(), p.getManagerMaxDiscountPercent(), p.getManagerMaxMarkupPercent(), p.getHighRiskDiscountPercent(), p.getMinimumPriceRatio(), p.getMinimumPrice(), p.getRules().stream().map(this::response).toList()); }
    private PricingRuleResponse response(PricingRule r) { return new PricingRuleResponse(r.getId(), r.getCategory(), r.getCalculationType(), r.getRuleKey(), r.getDescription(), r.getMinValue(), r.getMaxValue(), r.getFixedAmount(), r.getBaseAmount(), r.getPercentage(), r.getUnitSize(), r.getMultiplier(), r.getPriority(), r.isEnabled(), r.getPricingProfile(), r.getMediaComponent(), r.getIncludedQuantity(), r.getRoundingMode()); }
}
