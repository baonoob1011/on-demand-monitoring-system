package com.ondemandmonitoring.pricing.service;

import com.ondemandmonitoring.common.exception.*;
import com.ondemandmonitoring.pricing.domain.*;
import com.ondemandmonitoring.pricing.dto.*;
import com.ondemandmonitoring.pricing.enums.PricingPolicyStatus;
import com.ondemandmonitoring.pricing.repository.PricingPolicyRepository;
import java.time.Instant;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @RequiredArgsConstructor
public class PricingPolicyService {
    private final PricingPolicyRepository policies;

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
            rule.setPriority(rr.priority()); rule.setEnabled(rr.enabled()); rules.add(rule);
        }
        p.replaceRules(rules);
    }
    private void requireDraft(PricingPolicy p) { if (p.getStatus() != PricingPolicyStatus.DRAFT) throw new ApiException(ErrorCode.INVALID_REQUEST, "Published pricing policies are immutable"); }
    private PricingPolicyResponse response(PricingPolicy p) { return new PricingPolicyResponse(p.getId(), p.getName(), p.getPolicyVersion(), p.getStatus(), p.getCurrency(), p.getEffectiveFrom(), p.getEffectiveTo(), p.getManagerMaxDiscountPercent(), p.getManagerMaxMarkupPercent(), p.getHighRiskDiscountPercent(), p.getMinimumPriceRatio(), p.getMinimumPrice(), p.getRules().stream().map(this::response).toList()); }
    private PricingRuleResponse response(PricingRule r) { return new PricingRuleResponse(r.getId(), r.getCategory(), r.getCalculationType(), r.getRuleKey(), r.getDescription(), r.getMinValue(), r.getMaxValue(), r.getFixedAmount(), r.getBaseAmount(), r.getPercentage(), r.getUnitSize(), r.getMultiplier(), r.getPriority(), r.isEnabled()); }
}
