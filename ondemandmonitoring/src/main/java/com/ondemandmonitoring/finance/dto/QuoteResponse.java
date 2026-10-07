package com.ondemandmonitoring.finance.dto;

import com.ondemandmonitoring.finance.enums.QuoteStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record QuoteResponse(String id, String orderId, int version, QuoteStatus status,
                            BigDecimal packagePrice, BigDecimal additionalAmount,
                            BigDecimal discountAmount, BigDecimal adjustmentAmount,
                            BigDecimal totalAmount, String managerNote, String approvedByName,
                            Instant approvedAt, Instant acceptedAt, List<QuoteItemResponse> items) {}
