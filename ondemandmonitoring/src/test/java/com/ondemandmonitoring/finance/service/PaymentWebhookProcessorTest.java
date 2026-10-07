package com.ondemandmonitoring.finance.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.ondemandmonitoring.finance.domain.*;
import com.ondemandmonitoring.finance.enums.*;
import com.ondemandmonitoring.finance.gateway.VerifiedWebhook;
import com.ondemandmonitoring.finance.repository.*;
import com.ondemandmonitoring.finance.service.impl.MissionPaymentEligibilityService;
import com.ondemandmonitoring.finance.service.impl.PaymentWebhookProcessor;
import com.ondemandmonitoring.mission.domain.Mission;
import com.ondemandmonitoring.mission.enums.MissionStatus;
import com.ondemandmonitoring.mission.repository.MissionRepository;
import com.ondemandmonitoring.order.domain.Order;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PaymentWebhookProcessorTest {
    PaymentRepository payments = mock(PaymentRepository.class);
    InvoiceRepository invoices = mock(InvoiceRepository.class);
    MissionRepository missions = mock(MissionRepository.class);
    PaymentWebhookProcessor processor = new PaymentWebhookProcessor(payments, invoices, missions, new MissionPaymentEligibilityService());
    Payment payment; Invoice invoice; Mission mission;

    @BeforeEach void setUp() {
        Order order = new Order(); order.setId("o1");
        Quote quote = new Quote(); quote.setStatus(QuoteStatus.ACCEPTED_BY_CUSTOMER);
        invoice = new Invoice(); invoice.setId("i1"); invoice.setOrder(order); invoice.setQuote(quote);
        invoice.setTotalAmount(new BigDecimal("1000")); invoice.setDepositAmount(new BigDecimal("300"));
        invoice.setPaidAmount(BigDecimal.ZERO); invoice.setRemainingAmount(new BigDecimal("1000")); invoice.setStatus(InvoiceStatus.ISSUED);
        payment = new Payment(); payment.setId("p1"); payment.setInvoice(invoice); payment.setPaymentCode(123L);
        payment.setTransactionReference("123");
        payment.setAmount(new BigDecimal("300")); payment.setProvider(PaymentProvider.VNPAY); payment.setStatus(PaymentStatus.PENDING);
        mission = new Mission(); mission.setOrder(order); mission.setStatus(MissionStatus.WAITING_DEPOSIT);
        when(payments.findByTransactionReferenceForUpdate("123")).thenReturn(Optional.of(payment));
        when(invoices.findByIdForUpdate("i1")).thenReturn(Optional.of(invoice));
        when(missions.findByOrderIdForUpdate("o1")).thenReturn(Optional.of(mission));
    }

    @Test void wrongAmountDoesNotChangePayment() {
        assertThat(processor.process(webhook(299))).isEqualTo(PaymentWebhookResult.INVALID_AMOUNT);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
    }

    @Test void successfulDepositUpdatesInvoiceAndUnlocksMission() {
        when(payments.sumSuccessfulPayments("i1")).thenReturn(new BigDecimal("300"));
        assertThat(processor.process(webhook(300))).isEqualTo(PaymentWebhookResult.PROCESSED);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(invoice.getPaidAmount()).isEqualByComparingTo("300");
        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.PARTIALLY_PAID);
        assertThat(mission.getStatus()).isEqualTo(MissionStatus.RESOURCE_ASSIGNING);
    }

    @Test void duplicateSuccessWebhookDoesNotCreditAgain() {
        payment.setStatus(PaymentStatus.SUCCESS);
        assertThat(processor.process(webhook(300))).isEqualTo(PaymentWebhookResult.ALREADY_CONFIRMED);
        verify(invoices, never()).findByIdForUpdate(anyString());
        verify(payments, never()).sumSuccessfulPayments(anyString());
    }

    @Test void finalPaymentMakesInvoicePaid() {
        invoice.setPaidAmount(new BigDecimal("300")); payment.setAmount(new BigDecimal("700")); payment.setPaymentType(PaymentType.FINAL_PAYMENT);
        when(payments.sumSuccessfulPayments("i1")).thenReturn(new BigDecimal("1000"));
        processor.process(webhook(700));
        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.PAID);
        assertThat(invoice.getRemainingAmount()).isEqualByComparingTo("0");
    }

    @Test void unknownVnPayOrderIsReportedWithoutMutation() {
        when(payments.findByTransactionReferenceForUpdate("123")).thenReturn(Optional.empty());
        assertThat(processor.process(webhook(300))).isEqualTo(PaymentWebhookResult.ORDER_NOT_FOUND);
        verifyNoInteractions(invoices, missions);
    }

    private VerifiedWebhook webhook(long amount) {
        return new VerifiedWebhook("123", BigDecimal.valueOf(amount), "VND", "tx1", null,
                "00", "00", true);
    }
}
