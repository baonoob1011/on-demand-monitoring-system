package com.ondemandmonitoring.finance.mapper;

import com.ondemandmonitoring.finance.domain.*;
import com.ondemandmonitoring.finance.dto.*;
import java.util.Comparator;

public final class FinanceMapper {
    private FinanceMapper() {}

    public static QuoteResponse quote(Quote quote) {
        return new QuoteResponse(quote.getId(), quote.getOrder().getId(), quote.getQuoteVersion(), quote.getStatus(),
                quote.getPackagePrice(), quote.getAdditionalAmount(), quote.getDiscountAmount(),
                quote.getAdjustmentAmount(), quote.getTotalAmount(), quote.getManagerNote(),
                quote.getApprovedBy() == null ? null : quote.getApprovedBy().getFullName(),
                quote.getApprovedAt(), quote.getAcceptedAt(), quote.getItems().stream()
                .sorted(Comparator.comparingInt(QuoteItem::getDisplayOrder)).map(FinanceMapper::quoteItem).toList());
    }

    public static QuoteItemResponse quoteItem(QuoteItem item) {
        return new QuoteItemResponse(item.getId(), item.getOrderChecklistItem() == null ? null : item.getOrderChecklistItem().getId(),
                item.getType(), item.getDescription(), item.getQuantity(), item.getUnitPrice(), item.getAmount(), item.getDisplayOrder());
    }

    public static InvoiceResponse invoice(Invoice invoice, boolean finalPaymentAllowed) {
        return new InvoiceResponse(invoice.getId(), invoice.getInvoiceNumber(), invoice.getOrder().getId(),
                invoice.getQuote().getId(), invoice.getTotalAmount(), invoice.getDepositAmount(),
                invoice.getPaidAmount(), invoice.getRemainingAmount(), invoice.getStatus(), invoice.getIssuedAt(),
                finalPaymentAllowed);
    }

    public static PaymentResponse payment(Payment payment) {
        return new PaymentResponse(payment.getId(), payment.getInvoice().getId(), payment.getPaymentCode(),
                payment.getPaymentType(), payment.getAmount(), payment.getCurrency(), payment.getStatus(),
                payment.getProvider(), payment.getPaymentUrl(), payment.getTransactionReference(),
                payment.getProviderTransactionId(), payment.getPaidAt());
    }
}
