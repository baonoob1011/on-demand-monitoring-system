package com.ondemandmonitoring.finance.service;

import com.ondemandmonitoring.finance.dto.PaymentResponse;
import com.ondemandmonitoring.finance.enums.PaymentWebhookResult;
import com.ondemandmonitoring.finance.gateway.VerifiedWebhook;
import java.util.List;
import java.util.Map;

/**
 * Application contract for customer payment use cases.
 *
 * <p>Controllers depend on this interface so HTTP concerns stay outside payment
 * persistence, provider signing and invoice state transitions.</p>
 */
public interface IPaymentService {
    PaymentResponse createDeposit(String invoiceId, String clientIp);
    PaymentResponse createFinal(String invoiceId, String clientIp);
    PaymentResponse get(String paymentId);
    PaymentResponse getByReference(String transactionReference);
    PaymentResponse refreshByReference(String transactionReference, String requestIp);
    List<PaymentResponse> list(String invoiceId);
    PaymentWebhookResult processVnPayWebhook(Map<String, String> payload);
    VerifiedWebhook verifyVnPayReturn(Map<String, String> payload);
}
