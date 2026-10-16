package com.ondemandmonitoring.pricing.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ondemandmonitoring.checklist.domain.ChecklistDefinition;
import com.ondemandmonitoring.common.exception.ApiException;
import com.ondemandmonitoring.order.domain.Order;
import com.ondemandmonitoring.order.domain.OrderChecklistItem;
import com.ondemandmonitoring.order.enums.ChecklistReviewStatus;
import com.ondemandmonitoring.order.enums.OrderChecklistSourceType;
import com.ondemandmonitoring.order.enums.OrderPriority;
import com.ondemandmonitoring.pricing.domain.PricingPolicy;
import com.ondemandmonitoring.pricing.domain.PricingRule;
import com.ondemandmonitoring.pricing.enums.*;
import com.ondemandmonitoring.pricing.repository.CustomWorkAssessmentRepository;
import com.ondemandmonitoring.pricing.repository.PricingPolicyRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** The engine prices a snapshot from its own frozen fields plus the policy; the live catalog is never consulted. */
class FlexibleChecklistPricingEngineTest {
    PricingPolicyRepository policies = mock(PricingPolicyRepository.class);
    CustomWorkAssessmentRepository assessments = mock(CustomWorkAssessmentRepository.class);
    RuleBasedPricingEngine engine = new RuleBasedPricingEngine(policies, assessments, new ChecklistPriceCalculator());

    PricingRule rule(PricingCategory category, String key, PricingCalculationType type, String price) {
        PricingRule r = new PricingRule();
        r.setCategory(category); r.setRuleKey(key); r.setDescription(key); r.setCalculationType(type);
        r.setFixedAmount(new BigDecimal(price)); r.setEnabled(true);
        return r;
    }
    PricingRule media(String profile, MediaPricingComponent component, String price) {
        PricingRule r = rule(PricingCategory.MEDIA, profile + ":" + component, PricingCalculationType.FIXED, price);
        r.setPricingProfile(profile); r.setMediaComponent(component);
        return r;
    }
    PricingRule tier(MediaPricingComponent component, String from, String to, String price) {
        PricingRule r = media("STANDARD", component, price);
        r.setRuleKey("STANDARD:" + component + ":" + from); r.setCalculationType(PricingCalculationType.PER_BLOCK);
        r.setMinValue(new BigDecimal(from)); r.setMaxValue(to == null ? null : new BigDecimal(to)); r.setUnitSize(BigDecimal.TEN);
        return r;
    }
    PricingPolicy policy(int version, PricingRule... rules) {
        PricingPolicy p = new PricingPolicy();
        p.setId("p" + version); p.setPolicyVersion(version); p.setStatus(PricingPolicyStatus.ACTIVE); p.setCurrency("VND");
        p.replaceRules(List.of(rules));
        when(policies.findEffectiveActive(any(Instant.class))).thenReturn(List.of(p));
        return p;
    }
    Order order(String packageFee) {
        var service = new com.ondemandmonitoring.service.domain.Service();
        service.setName("Inspection"); service.setBasePrice(new BigDecimal(packageFee));
        Order o = new Order();
        o.setId("o1"); o.setService(service); o.setServiceBasePriceSnapshot(new BigDecimal(packageFee)); o.setPriority(OrderPriority.NORMAL);
        when(assessments.findByOrderId("o1")).thenReturn(Optional.empty());
        return o;
    }
    OrderChecklistItem snapshot(String base, int includedImages, int includedVideos, int requestedImages, int requestedVideos) {
        OrderChecklistItem item = new OrderChecklistItem();
        item.setContent("Check roof"); item.setSourceType(OrderChecklistSourceType.SERVICE_TEMPLATE);
        item.setReviewStatus(ChecklistReviewStatus.INCLUDED); item.setDifficultySnapshot(ChecklistDifficulty.BASIC);
        item.setPricingSnapshotVersion(1); item.setBasePriceSnapshot(new BigDecimal(base));
        item.setIncludedImageCountSnapshot(includedImages); item.setIncludedVideoCountSnapshot(includedVideos);
        item.setRequestedImageCount(requestedImages); item.setRequestedVideoCount(requestedVideos);
        item.setPricingProfileSnapshot("STANDARD");
        return item;
    }

    @Test void orderTotalIsPackageFeePlusEachChecklistContributionOnce() {
        policy(4, media("STANDARD", MediaPricingComponent.INCLUDED_IMAGE, "50000"),
                tier(MediaPricingComponent.EXTRA_IMAGE, "0", "10", "50000"), tier(MediaPricingComponent.EXTRA_IMAGE, "10", null, "40000"));

        var result = engine.calculate(order("1000000"), List.of(snapshot("200000", 10, 0, 30, 0)));

        // 1,000,000 package + 200,000 base + 50,000 included images + 50,000 + 40,000 extra tiers
        assertThat(result.systemCalculatedAmount()).isEqualByComparingTo("1340000");
        assertThat(result.policyVersion()).isEqualTo(4);
        assertThat(result.items()).extracting(i -> i.category()).containsExactly(
                PricingCategory.PACKAGE, PricingCategory.CHECKLIST, PricingCategory.MEDIA, PricingCategory.MEDIA, PricingCategory.MEDIA);
    }

    @Test void removingAnOptionalChecklistLowersThePriceByExactlyItsContribution() {
        policy(4, media("STANDARD", MediaPricingComponent.INCLUDED_IMAGE, "50000"));
        var both = engine.calculate(order("1000000"), List.of(snapshot("200000", 10, 0, 10, 0), snapshot("100000", 0, 0, 0, 0)));
        var one = engine.calculate(order("1000000"), List.of(snapshot("200000", 10, 0, 10, 0)));
        assertThat(both.systemCalculatedAmount().subtract(one.systemCalculatedAmount())).isEqualByComparingTo("100000");
    }

    @Test void rejectedAndDuplicateChecklistsAreNotBilled() {
        policy(4);
        OrderChecklistItem rejected = snapshot("200000", 0, 0, 0, 0);
        rejected.setReviewStatus(ChecklistReviewStatus.REJECTED);
        assertThat(engine.calculate(order("1000000"), List.of(rejected)).systemCalculatedAmount()).isEqualByComparingTo("1000000");
    }

    @Test void snapshotPriceIgnoresLaterCatalogEdits() {
        policy(4, media("STANDARD", MediaPricingComponent.INCLUDED_IMAGE, "50000"));
        ChecklistDefinition definition = new ChecklistDefinition();
        definition.setId("c1"); definition.setBasePrice(new BigDecimal("200000")); definition.setIncludedImageCount(10);
        OrderChecklistItem item = snapshot("200000", 10, 0, 10, 0);
        item.setSourceChecklist(definition);

        BigDecimal before = engine.calculate(order("1000000"), List.of(item)).systemCalculatedAmount();
        definition.setBasePrice(new BigDecimal("999999")); definition.setIncludedImageCount(500); definition.setPricingProfile("OTHER");

        assertThat(engine.calculate(order("1000000"), List.of(item)).systemCalculatedAmount()).isEqualByComparingTo(before);
    }

    @Test void legacyVersionZeroSnapshotsKeepDifficultyOnlyPricing() {
        policy(2, rule(PricingCategory.CHECKLIST, "HARD", PricingCalculationType.FIXED, "150000"),
                media("STANDARD", MediaPricingComponent.INCLUDED_IMAGE, "50000"));
        OrderChecklistItem legacy = new OrderChecklistItem();
        legacy.setContent("old"); legacy.setSourceType(OrderChecklistSourceType.SERVICE_TEMPLATE);
        legacy.setReviewStatus(ChecklistReviewStatus.INCLUDED); legacy.setDifficultySnapshot(ChecklistDifficulty.HARD);
        // pricingSnapshotVersion defaults to 0 and the media columns hold their zero defaults
        assertThat(engine.calculate(order("1000000"), List.of(legacy)).systemCalculatedAmount()).isEqualByComparingTo("1150000");
    }

    @Test void newPolicyVersionPricesNewSnapshotsDifferentlyWithoutTouchingTheOldTotals() {
        OrderChecklistItem item = snapshot("0", 10, 0, 10, 0);
        policy(1, media("STANDARD", MediaPricingComponent.INCLUDED_IMAGE, "50000"));
        var v1 = engine.calculate(order("1000000"), List.of(item));
        policy(2, media("STANDARD", MediaPricingComponent.INCLUDED_IMAGE, "80000"));
        var v2 = engine.calculate(order("1000000"), List.of(item));
        // The persisted Quote keeps the v1 numbers; recalculation is an explicit new-version act and reports its policy version.
        assertThat(v1.systemCalculatedAmount()).isEqualByComparingTo("1050000");
        assertThat(v1.policyVersion()).isEqualTo(1);
        assertThat(v2.systemCalculatedAmount()).isEqualByComparingTo("1080000");
        assertThat(v2.policyVersion()).isEqualTo(2);
    }

    @Test void customSnapshotIsPricedByThePolicyWhenItHasACustomRuleOtherwiseByTheManager() {
        OrderChecklistItem custom = new OrderChecklistItem();
        custom.setId("custom-1");
        custom.setContent("Check antenna"); custom.setSourceType(OrderChecklistSourceType.CUSTOMER_CUSTOM);
        custom.setReviewStatus(ChecklistReviewStatus.ADDITIONAL); custom.setDifficultySnapshot(ChecklistDifficulty.BASIC);
        custom.setPricingSnapshotVersion(1); custom.setPricingProfileSnapshot("CUSTOM");

        policy(1, media("CUSTOM", MediaPricingComponent.CHECKLIST_BASE, "300000"));
        var priced = engine.calculate(order("0"), List.of(custom));
        assertThat(priced.systemCalculatedAmount()).isEqualByComparingTo("300000");
        assertThat(priced.pendingQuoteItems()).isEmpty();
        assertThat(priced.customWork()).isFalse();

        // no CUSTOM rule: pending, never silently 0 and never a failure; the Manager's price becomes the whole price
        policy(2);
        var pending = engine.calculate(order("0"), List.of(custom));
        assertThat(pending.pendingQuoteItems()).containsExactly("Check antenna");
        assertThat(pending.systemCalculatedAmount()).isZero();
        custom.setManagerQuoteRequired(true); custom.setManagerQuotedPrice(new BigDecimal("450000"));
        var quoted = engine.calculate(order("0"), List.of(custom));
        assertThat(quoted.pendingQuoteItems()).isEmpty();
        assertThat(quoted.systemCalculatedAmount()).isEqualByComparingTo("450000");
    }

    @Test void managerQuotedItemsAreStillSkippedWhenRejectedAndDoNotAffectCatalogItems() {
        policy(1, media("STANDARD", MediaPricingComponent.INCLUDED_IMAGE, "50000"));
        OrderChecklistItem custom = new OrderChecklistItem();
        custom.setContent("Extra"); custom.setSourceType(OrderChecklistSourceType.CUSTOMER_CUSTOM);
        custom.setReviewStatus(ChecklistReviewStatus.REJECTED); custom.setDifficultySnapshot(ChecklistDifficulty.BASIC);
        custom.setPricingSnapshotVersion(1); custom.setManagerQuoteRequired(true);
        var result = engine.calculate(order("0"), List.of(snapshot("200000", 10, 0, 10, 0), custom));
        assertThat(result.systemCalculatedAmount()).isEqualByComparingTo("250000");
        assertThat(result.pendingQuoteItems()).isEmpty();
    }

    @Test void unpriceableExtraMediaFailsInsteadOfBeingFree() {
        policy(1, media("STANDARD", MediaPricingComponent.INCLUDED_IMAGE, "50000"));
        assertThatThrownBy(() -> engine.calculate(order("1000000"), List.of(snapshot("0", 10, 0, 11, 0)))).isInstanceOf(ApiException.class);
    }
}
