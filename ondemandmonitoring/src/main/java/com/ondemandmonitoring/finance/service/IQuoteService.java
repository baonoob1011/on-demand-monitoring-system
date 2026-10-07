package com.ondemandmonitoring.finance.service;

import com.ondemandmonitoring.finance.dto.*;
import com.ondemandmonitoring.order.dto.response.OrderChecklistItemResponse;

/** Application contract for quote review, approval and customer acceptance. */
public interface IQuoteService {
    PricingReviewResponse getPricingReview(String orderId);
    OrderChecklistItemResponse reviewChecklist(String orderId, String itemId, ChecklistReviewRequest request);
    QuoteResponse saveDraft(String orderId, QuoteDraftRequest request);
    QuoteResponse approve(String orderId, String quoteId);
    QuoteResponse getCurrentForCustomer(String orderId);
    InvoiceResponse accept(String orderId, String quoteId);
    InvoiceResponse getInvoiceByOrder(String orderId);
}
