package com.ondemandmonitoring.finance.dto;

import com.ondemandmonitoring.order.dto.response.OrderChecklistItemResponse;
import java.util.List;
import java.math.BigDecimal;

public record PricingReviewResponse(String orderId, String orderCode, String customerName,
                                    String serviceName, BigDecimal basePackagePrice,
                                    List<OrderChecklistItemResponse> checklistItems,
                                    QuoteResponse currentQuote, List<QuoteResponse> quoteHistory) {}
