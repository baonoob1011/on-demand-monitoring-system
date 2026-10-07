package com.ondemandmonitoring.finance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ondemandmonitoring.finance.dto.PaymentResponse;
import com.ondemandmonitoring.finance.enums.PaymentProvider;
import com.ondemandmonitoring.finance.enums.PaymentStatus;
import com.ondemandmonitoring.finance.enums.PaymentType;
import com.ondemandmonitoring.finance.gateway.PaymentGateway;
import com.ondemandmonitoring.finance.gateway.PaymentQueryCommand;
import com.ondemandmonitoring.finance.gateway.PaymentStatusGateway;
import com.ondemandmonitoring.finance.gateway.VerifiedWebhook;
import java.math.BigDecimal;

import com.ondemandmonitoring.finance.record.PaymentReconciliationContext;
import com.ondemandmonitoring.finance.service.impl.PaymentService;
import org.junit.jupiter.api.Test;

class PaymentServiceTest {
    private final IPaymentPersistenceService persistence = mock(IPaymentPersistenceService.class);
    private final IPaymentWebhookService webhook = mock(IPaymentWebhookService.class);
    private final PaymentGateway paymentGateway = mock(PaymentGateway.class);
    private final PaymentStatusGateway statusGateway = mock(PaymentStatusGateway.class);
    private final PaymentService service = new PaymentService(persistence, webhook, paymentGateway, statusGateway);

    @Test
    void refreshUsesQueryDrAndAppliesVerifiedSuccess() {
        PaymentResponse pending = payment(PaymentStatus.PENDING);
        PaymentResponse success = payment(PaymentStatus.SUCCESS);
        when(persistence.getByReference("123456")).thenReturn(pending, success);
        when(persistence.reconciliationContext("123456")).thenReturn(
                new PaymentReconciliationContext("123456", null, "20261007085000", null));
        VerifiedWebhook verified = new VerifiedWebhook("123456", new BigDecimal("300000"), "VND",
                "14587452", "20261007090000", "00", "00", true);
        when(statusGateway.queryPayment(any(PaymentQueryCommand.class))).thenReturn(verified);

        PaymentResponse result = service.refreshByReference("123456", "127.0.0.1");

        assertThat(result.status()).isEqualTo(PaymentStatus.SUCCESS);
        verify(webhook).process(verified);
    }

    private PaymentResponse payment(PaymentStatus status) {
        return new PaymentResponse("p1", "i1", 123456L, PaymentType.DEPOSIT,
                new BigDecimal("300000"), "VND", status, PaymentProvider.VNPAY,
                "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html", "123456", null, null);
    }
}
