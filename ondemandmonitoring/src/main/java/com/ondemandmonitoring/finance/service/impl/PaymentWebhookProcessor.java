package com.ondemandmonitoring.finance.service.impl;

import com.ondemandmonitoring.common.exception.ApiException;
import com.ondemandmonitoring.common.exception.ErrorCode;
import com.ondemandmonitoring.delivery.service.IDeliveryWorkflowService;
import com.ondemandmonitoring.finance.domain.Invoice;
import com.ondemandmonitoring.finance.domain.Payment;
import com.ondemandmonitoring.finance.enums.*;
import com.ondemandmonitoring.finance.gateway.VerifiedWebhook;
import com.ondemandmonitoring.finance.repository.InvoiceRepository;
import com.ondemandmonitoring.finance.repository.PaymentRepository;
import com.ondemandmonitoring.finance.service.IMissionPaymentEligibilityService;
import com.ondemandmonitoring.finance.service.IPaymentWebhookService;
import com.ondemandmonitoring.mission.repository.MissionRepository;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PaymentWebhookProcessor implements IPaymentWebhookService {
    private final PaymentRepository payments;
    private final InvoiceRepository invoices;
    private final MissionRepository missions;
    private final IMissionPaymentEligibilityService eligibility;
    private final IDeliveryWorkflowService deliveryWorkflow;

    @Transactional
    public PaymentWebhookResult process(VerifiedWebhook webhook) {
        Payment payment = payments.findByTransactionReferenceForUpdate(webhook.transactionReference()).orElse(null);
        if (payment == null || payment.getProvider() != PaymentProvider.VNPAY) {
            return PaymentWebhookResult.ORDER_NOT_FOUND;
        }
        if (!"VND".equalsIgnoreCase(webhook.currency())
                || payment.getAmount().compareTo(webhook.amount()) != 0) {
            return PaymentWebhookResult.INVALID_AMOUNT;
        }
        if (payment.getStatus() == PaymentStatus.SUCCESS) return PaymentWebhookResult.ALREADY_CONFIRMED;
        if (payment.getStatus() == PaymentStatus.CANCELLED
                || payment.getStatus() == PaymentStatus.REFUNDED
                || payment.getStatus() == PaymentStatus.FAILED) {
            return PaymentWebhookResult.ALREADY_CONFIRMED;
        }
        if (!webhook.successful()) {
            if (payment.getStatus() == PaymentStatus.PENDING || payment.getStatus() == PaymentStatus.PROCESSING) {
                payment.setStatus(PaymentStatus.FAILED);
                payment.setProviderTransactionId(webhook.providerTransactionId());
                payment.setFailureReason("VNPAY response=" + webhook.responseCode()
                        + ", transactionStatus=" + webhook.transactionStatus());
            }
            return PaymentWebhookResult.IGNORED;
        }

        Invoice invoice = invoices.findByIdForUpdate(payment.getInvoice().getId())
                .orElseThrow(() -> new ApiException(ErrorCode.INVOICE_NOT_FOUND));
        payment.setStatus(PaymentStatus.SUCCESS);
        payment.setProviderTransactionId(webhook.providerTransactionId());
        payment.setPaidAt(Instant.now());
        payments.flush();

        BigDecimal paid = payments.sumSuccessfulPayments(invoice.getId());
        if (paid.compareTo(invoice.getTotalAmount()) > 0) throw new ApiException(ErrorCode.PAYMENT_AMOUNT_MISMATCH, "Payment would overpay invoice");
        invoice.setPaidAmount(paid);
        invoice.setRemainingAmount(invoice.getTotalAmount().subtract(paid).max(BigDecimal.ZERO));
        invoice.setStatus(paid.signum() == 0 ? InvoiceStatus.ISSUED
                : paid.compareTo(invoice.getTotalAmount()) >= 0 ? InvoiceStatus.PAID : InvoiceStatus.PARTIALLY_PAID);
        missions.findByOrderIdForUpdate(invoice.getOrder().getId()).ifPresent(mission -> eligibility.unlockIfEligible(invoice, mission));
        if (payment.getPaymentType() == PaymentType.FINAL_PAYMENT && invoice.getStatus() == InvoiceStatus.PAID) {
            deliveryWorkflow.confirmFinalPayment(invoice.getOrder().getId());
        }
        return PaymentWebhookResult.PROCESSED;
    }
}
