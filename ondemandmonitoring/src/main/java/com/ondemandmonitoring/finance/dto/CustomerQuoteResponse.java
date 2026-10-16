package com.ondemandmonitoring.finance.dto;

import com.ondemandmonitoring.finance.enums.QuoteStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Customer-safe quote projection. Internal pricing approval notes and risk flags are intentionally excluded. */
public record CustomerQuoteResponse(String id, String orderId, int version, QuoteStatus status,
        BigDecimal packagePrice, BigDecimal additionalAmount, BigDecimal discountAmount,
        BigDecimal adjustmentAmount, BigDecimal totalAmount, String managerNote,
        Instant approvedAt, Instant acceptedAt, List<QuoteItemResponse> items) {}
