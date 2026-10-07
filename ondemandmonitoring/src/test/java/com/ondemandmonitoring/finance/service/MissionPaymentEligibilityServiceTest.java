package com.ondemandmonitoring.finance.service;

import static org.assertj.core.api.Assertions.assertThat;
import com.ondemandmonitoring.finance.domain.Invoice;
import com.ondemandmonitoring.finance.domain.Quote;
import com.ondemandmonitoring.finance.enums.QuoteStatus;
import com.ondemandmonitoring.finance.service.impl.MissionPaymentEligibilityService;
import com.ondemandmonitoring.mission.domain.Mission;
import com.ondemandmonitoring.mission.enums.MissionStatus;
import com.ondemandmonitoring.order.domain.Order;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class MissionPaymentEligibilityServiceTest {
    private final MissionPaymentEligibilityService service = new MissionPaymentEligibilityService();

    @Test void depositIsSatisfiedAtOrAboveRequirement() {
        Invoice invoice = invoice("300", "300");
        assertThat(service.isDepositSatisfied(invoice)).isTrue();
        invoice.setPaidAmount(new BigDecimal("299"));
        assertThat(service.isDepositSatisfied(invoice)).isFalse();
    }

    @Test void unlockRequiresAcceptedQuoteAndWaitingMissionForSameOrder() {
        Order order = new Order(); order.setId("order-1");
        Invoice invoice = invoice("300", "300"); invoice.setOrder(order);
        Quote quote = new Quote(); quote.setStatus(QuoteStatus.ACCEPTED_BY_CUSTOMER); invoice.setQuote(quote);
        Mission mission = new Mission(); mission.setOrder(order); mission.setStatus(MissionStatus.WAITING_DEPOSIT);
        assertThat(service.unlockIfEligible(invoice, mission)).isTrue();
        assertThat(mission.getStatus()).isEqualTo(MissionStatus.RESOURCE_ASSIGNING);
    }

    @Test void insufficientDepositDoesNotUnlockMission() {
        Order order = new Order(); order.setId("order-1");
        Invoice invoice = invoice("300", "299"); invoice.setOrder(order);
        Quote quote = new Quote(); quote.setStatus(QuoteStatus.ACCEPTED_BY_CUSTOMER); invoice.setQuote(quote);
        Mission mission = new Mission(); mission.setOrder(order); mission.setStatus(MissionStatus.WAITING_DEPOSIT);
        assertThat(service.unlockIfEligible(invoice, mission)).isFalse();
        assertThat(mission.getStatus()).isEqualTo(MissionStatus.WAITING_DEPOSIT);
    }

    private Invoice invoice(String deposit, String paid) {
        Invoice invoice = new Invoice();
        invoice.setDepositAmount(new BigDecimal(deposit)); invoice.setPaidAmount(new BigDecimal(paid));
        invoice.setTotalAmount(new BigDecimal("1000"));
        return invoice;
    }
}
