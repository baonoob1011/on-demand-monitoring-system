package com.ondemandmonitoring.pricing.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ondemandmonitoring.checklist.domain.ChecklistDefinition;
import com.ondemandmonitoring.checklist.domain.ServiceChecklist;
import com.ondemandmonitoring.checklist.repository.ChecklistDefinitionRepository;
import com.ondemandmonitoring.checklist.repository.ServiceChecklistRepository;
import com.ondemandmonitoring.common.exception.ApiException;
import com.ondemandmonitoring.common.exception.ErrorCode;
import com.ondemandmonitoring.pricing.domain.PricingPolicy;
import com.ondemandmonitoring.pricing.domain.PricingRule;
import com.ondemandmonitoring.pricing.dto.ChecklistPricingPreviewRequests.*;
import com.ondemandmonitoring.pricing.enums.*;
import com.ondemandmonitoring.pricing.repository.PricingPolicyRepository;
import com.ondemandmonitoring.service.domain.Service;
import com.ondemandmonitoring.service.repository.ServiceRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ChecklistPricingPreviewServiceTest {
    ServiceRepository services = mock(ServiceRepository.class);
    ChecklistDefinitionRepository definitions = mock(ChecklistDefinitionRepository.class);
    ServiceChecklistRepository assignments = mock(ServiceChecklistRepository.class);
    PricingPolicyRepository policies = mock(PricingPolicyRepository.class);
    ChecklistPricingPreviewService preview = new ChecklistPricingPreviewService(services, definitions, assignments, policies, new ChecklistPriceCalculator(),
            new ServicePriceResolver(assignments, policies, new ChecklistPriceCalculator()));
    Service inspection, survey;
    ChecklistDefinition roof, wall;
    List<ServiceChecklist> inspectionRows = new ArrayList<>();

    @BeforeEach void setup() {
        inspection = Service.builder().name("Inspection").isActive(true).basePrice(new BigDecimal("1000000")).build(); inspection.setId("s1");
        survey = Service.builder().name("Survey").isActive(true).basePrice(new BigDecimal("500000")).build(); survey.setId("s2");
        roof = checklist("c1", "Check roof", "200000", 10, 1);
        wall = checklist("c2", "Check wall", "100000", 0, 0);
        inspectionRows.add(row(inspection, roof, 0)); inspectionRows.add(row(inspection, wall, 1));
        when(services.findById("s1")).thenReturn(Optional.of(inspection));
        when(services.findById("s2")).thenReturn(Optional.of(survey));
        when(assignments.findAllByServiceIdOrderByDisplayOrderAscIdAsc("s1")).thenAnswer(i -> inspectionRows);
        when(assignments.findAllByServiceIdAndChecklistIsActiveTrueOrderByDisplayOrderAscIdAsc("s1")).thenAnswer(i -> inspectionRows);
        when(assignments.findAllByServiceIdOrderByDisplayOrderAscIdAsc("s2")).thenReturn(List.of(row(survey, roof, 0)));
        when(definitions.findById("c1")).thenReturn(Optional.of(roof));
        when(definitions.findById("c2")).thenReturn(Optional.of(wall));
        PricingPolicy p = new PricingPolicy(); p.setId("p1"); p.setPolicyVersion(5); p.setCurrency("VND"); p.setStatus(PricingPolicyStatus.ACTIVE);
        p.replaceRules(List.of(included(MediaPricingComponent.INCLUDED_IMAGE, "50000"), included(MediaPricingComponent.INCLUDED_VIDEO, "120000"),
                tier(MediaPricingComponent.EXTRA_IMAGE, "0", "10", "50000"), tier(MediaPricingComponent.EXTRA_IMAGE, "10", null, "40000"),
                tier(MediaPricingComponent.EXTRA_VIDEO, "0", null, "90000")));
        when(policies.findEffectiveActive(any(Instant.class))).thenReturn(List.of(p));
    }

    ChecklistDefinition checklist(String id, String content, String base, int images, int videos) {
        var c = new ChecklistDefinition();
        c.setId(id); c.setContent(content); c.setIsActive(true); c.setBasePrice(new BigDecimal(base));
        c.setIncludedImageCount(images); c.setIncludedVideoCount(videos); c.setPricingProfile("STANDARD");
        return c;
    }
    ServiceChecklist row(Service s, ChecklistDefinition c, int order) {
        var r = new ServiceChecklist(); r.setId("sc-" + s.getId() + c.getId()); r.setService(s); r.setChecklist(c); r.setDisplayOrder(order); return r;
    }
    PricingRule included(MediaPricingComponent component, String price) {
        var r = new PricingRule();
        r.setCategory(PricingCategory.MEDIA); r.setPricingProfile("STANDARD"); r.setMediaComponent(component); r.setRuleKey(component.name());
        r.setDescription(component.name()); r.setCalculationType(PricingCalculationType.FIXED); r.setFixedAmount(new BigDecimal(price)); r.setEnabled(true);
        return r;
    }
    PricingRule tier(MediaPricingComponent component, String from, String to, String price) {
        var r = included(component, price);
        r.setRuleKey(component + from); r.setMinValue(new BigDecimal(from)); r.setMaxValue(to == null ? null : new BigDecimal(to));
        r.setUnitSize(component == MediaPricingComponent.EXTRA_VIDEO ? BigDecimal.ONE : BigDecimal.TEN); r.setRoundingMode(PricingRounding.CEILING);
        return r;
    }

    @Test void defaultServicePriceIsOnlyTheSumOfDefaultChecklistContributions() {
        var result = preview.defaultPrice("s1");
        // roof: 200,000 + 50,000 (10 images) + 120,000 (1 video); wall: 100,000
        assertThat(result.checklists()).extracting(c -> c.subtotal().intValue()).containsExactly(370000, 100000);
        assertThat(result.checklistSubtotal()).isEqualByComparingTo("470000");
        // the legacy catalog price (1,000,000) is NOT added on top of priced checklists
        assertThat(result.priceSource()).isEqualTo("CHECKLISTS");
        assertThat(result.packageFee()).isZero();
        assertThat(result.total()).isEqualByComparingTo("470000");
        assertThat(result.policyVersion()).isEqualTo(5);
        assertThat(result.complete()).isTrue();
    }

    @Test void addingAndRemovingADefaultChecklistChangesThePriceByItsContribution() {
        BigDecimal both = preview.defaultPrice("s1").total();
        inspectionRows.remove(1);
        BigDecimal withoutWall = preview.defaultPrice("s1").total();
        assertThat(both.subtract(withoutWall)).isEqualByComparingTo("100000");
        inspectionRows.clear();
        assertThat(preview.defaultPrice("s1").total()).isEqualByComparingTo("1000000");
    }

    @Test void adminWhatIfDoesNotNeedSavedConfigurationAndLeavesTheCatalogUntouched() {
        var result = preview.adminPreview(new Admin("s1", new BigDecimal("900000"), null, List.of(
                new AdminItem("c1", new BigDecimal("300000"), 20, 0, null, 35, null),
                new AdminItem("c2", null, null, null, null, null, null))));

        // roof: 300,000 + 50,000 included images + 15 extra images (10 @50,000 + 5 -> 1 block @40,000) = 440,000; wall 100,000
        assertThat(result.checklists().get(0).subtotal()).isEqualByComparingTo("440000");
        // typed package fee 900,000 is ignored: roof 440,000 + wall 100,000 are the whole default price
        assertThat(result.priceSource()).isEqualTo("CHECKLISTS");
        assertThat(result.total()).isEqualByComparingTo("540000");
        assertThat(roof.getBasePrice()).isEqualByComparingTo("200000");
        assertThat(roof.getIncludedImageCount()).isEqualTo(10);
    }

    @Test void associationOverrideAffectsOnlyThatServiceAndInheritsOtherwise() {
        inspectionRows.getFirst().setBasePriceOverride(new BigDecimal("260000"));
        inspectionRows.getFirst().setIncludedVideoCountOverride(0);
        var inspectionPrice = preview.defaultPrice("s1");
        var surveyPrice = preview.defaultPrice("s2");

        assertThat(inspectionPrice.checklists().getFirst().subtotal()).isEqualByComparingTo("310000"); // 260k + 50k, no video
        assertThat(surveyPrice.checklists().getFirst().subtotal()).isEqualByComparingTo("370000");     // inherits the definition
        assertThat(surveyPrice.packageFee()).isZero();
        assertThat(surveyPrice.total()).isEqualByComparingTo("370000");
    }

    @Test void adminPreviewReportsAChecklistThePolicyCannotPriceInsteadOfASmallerTotal() {
        PricingPolicy noTiers = new PricingPolicy(); noTiers.setId("notiers"); noTiers.setPolicyVersion(9); noTiers.setCurrency("VND"); noTiers.setStatus(PricingPolicyStatus.DRAFT);
        noTiers.replaceRules(List.of(included(MediaPricingComponent.INCLUDED_IMAGE, "50000")));
        when(policies.findById("notiers")).thenReturn(Optional.of(noTiers));
        var result = preview.adminPreview(new Admin("s1", null, "notiers", List.of(new AdminItem("c1", null, null, null, null, 20, null))));
        assertThat(result.complete()).isFalse();
        assertThat(result.warnings()).hasSize(1);
    }

    @Test void adminCanPreviewAgainstADraftPolicyBeforePublishing() {
        PricingPolicy draft = new PricingPolicy(); draft.setId("draft"); draft.setPolicyVersion(6); draft.setCurrency("VND"); draft.setStatus(PricingPolicyStatus.DRAFT);
        draft.replaceRules(List.of(included(MediaPricingComponent.INCLUDED_IMAGE, "70000")));
        when(policies.findById("draft")).thenReturn(Optional.of(draft));
        var result = preview.adminPreview(new Admin("s1", null, "draft", null));
        assertThat(result.draftPolicy()).isTrue();
        assertThat(result.checklists().getFirst().subtotal()).isEqualByComparingTo("270000");
    }

    @Test void customerQuoteUsesBackendPricesAndTheRequestedQuantities() {
        var result = preview.customerQuote(new Customer("s1", List.of(new CustomerItem("c1", null, 30, 3), new CustomerItem("c2", null, null, null))));
        // roof: 200k + 50k + 120k + (50k+40k) images + 2 extra videos x 90k
        assertThat(result.checklists().getFirst().subtotal()).isEqualByComparingTo("640000");
        assertThat(result.checklists().getFirst().extraImageCount()).isEqualTo(20);
        assertThat(result.checklists().getFirst().extraVideoCount()).isEqualTo(2);
        assertThat(result.packageFee()).isZero();
        assertThat(result.total()).isEqualByComparingTo("740000");
    }

    @Test void withoutAnyPricedChecklistTheLegacyCatalogPriceIsTheExplicitFallback() {
        roof.setBasePrice(BigDecimal.ZERO); roof.setIncludedImageCount(0); roof.setIncludedVideoCount(0);
        wall.setBasePrice(BigDecimal.ZERO);
        var stored = preview.defaultPrice("s1");
        assertThat(stored.priceSource()).isEqualTo("LEGACY_BASE_PRICE");
        assertThat(stored.packageFee()).isEqualByComparingTo("1000000");
        assertThat(stored.checklistSubtotal()).isZero();
        assertThat(stored.total()).isEqualByComparingTo("1000000");
        var whatIf = preview.adminPreview(new Admin("s1", new BigDecimal("900000"), null, null));
        assertThat(whatIf.total()).isEqualByComparingTo("900000");
        var customer = preview.customerQuote(new Customer("s1", null));
        assertThat(customer.priceSource()).isEqualTo("LEGACY_BASE_PRICE");
        assertThat(customer.total()).isEqualByComparingTo("1000000");
    }

    @Test void adminPreviewCustomerQuoteAndServiceEstimateReportTheSameDefaultTotal() {
        var resolver = new ServicePriceResolver(assignments, policies, new ChecklistPriceCalculator());
        var admin = preview.defaultPrice("s1");
        var customer = preview.customerQuote(new Customer("s1", null));
        var estimate = resolver.resolveOrLegacy(inspection);
        assertThat(customer.total()).isEqualByComparingTo(admin.total());
        assertThat(estimate.total()).isEqualByComparingTo(admin.total());
        assertThat(estimate.source()).isEqualTo(admin.priceSource());
    }

    @Test void estimateFallsBackToLegacyWithoutAnActivePolicyInsteadOfFailing() {
        when(policies.findEffectiveActive(any(Instant.class))).thenReturn(List.of());
        var resolver = new ServicePriceResolver(assignments, policies, new ChecklistPriceCalculator());
        var estimate = resolver.resolveOrLegacy(inspection);
        assertThat(estimate.source()).isEqualTo("LEGACY_BASE_PRICE");
        assertThat(estimate.total()).isEqualByComparingTo("1000000");
    }

    @Test void customerCannotAddAChecklistOutsideTheTemplateAsAnIdOrDuplicateOne() {
        assertThatThrownBy(() -> preview.customerQuote(new Customer("s1", List.of(new CustomerItem("not-in-template", null, null, null)))))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.ORDER_CHECKLIST_TEMPLATE_CHANGED));
        assertThatThrownBy(() -> preview.customerQuote(new Customer("s1", List.of(new CustomerItem("c1", null, null, null), new CustomerItem("c1", null, null, null)))))
                .isInstanceOf(ApiException.class);
    }

    @Test void customChecklistWithoutAPolicyPriceIsMarkedForManagerQuoteInsteadOfBeingRejectedOrFree() {
        var result = preview.customerQuote(new Customer("s1", List.of(new CustomerItem("c1", null, null, null),
                new CustomerItem(null, "Check the antenna mast", 2, null))));
        assertThat(result.quotePending()).isTrue();
        var custom = result.checklists().get(1);
        assertThat(custom.quotePending()).isTrue();
        assertThat(custom.custom()).isTrue();
        assertThat(custom.lines()).isEmpty();
        // the catalog checklist keeps its price; the pending one adds nothing until the Manager quotes it
        assertThat(result.checklistSubtotal()).isEqualByComparingTo(result.checklists().getFirst().subtotal());
    }

    @Test void requiredChecklistsStayInTheCustomerSelection() {
        inspectionRows.get(1).setRequired(true);
        assertThatThrownBy(() -> preview.customerQuote(new Customer("s1", List.of(new CustomerItem("c1", null, null, null)))))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.ORDER_CHECKLIST_REQUIRED_MISSING));
    }
}
