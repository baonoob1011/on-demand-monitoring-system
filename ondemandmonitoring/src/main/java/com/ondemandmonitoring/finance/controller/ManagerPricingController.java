package com.ondemandmonitoring.finance.controller;

import com.ondemandmonitoring.common.api.ApiResponse;
import com.ondemandmonitoring.finance.dto.*;
import com.ondemandmonitoring.finance.service.IQuoteService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import com.ondemandmonitoring.pricing.dto.*;
import com.ondemandmonitoring.pricing.service.CustomWorkAssessmentService;

@RestController
@RequestMapping("/api/manager/orders/{orderId}")
@PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
@RequiredArgsConstructor
public class ManagerPricingController {
    private final IQuoteService quoteService;
    private final CustomWorkAssessmentService assessmentService;

    @GetMapping("/pricing")
    public ApiResponse<PricingReviewResponse> pricing(@PathVariable String orderId) {
        return ApiResponse.ok(quoteService.getPricingReview(orderId));
    }

    @GetMapping("/invoice")
    public ApiResponse<InvoiceResponse> invoice(@PathVariable String orderId) {
        return ApiResponse.ok(quoteService.getInvoiceByOrder(orderId));
    }

    @PatchMapping("/checklist-items/{itemId}")
    public ApiResponse<com.ondemandmonitoring.order.dto.response.OrderChecklistItemResponse> review(
            @PathVariable String orderId, @PathVariable String itemId,
            @Valid @RequestBody ChecklistReviewRequest request) {
        return ApiResponse.ok("Checklist review saved", quoteService.reviewChecklist(orderId, itemId, request));
    }

    @PutMapping("/quotes/draft")
    public ApiResponse<QuoteResponse> saveDraft(@PathVariable String orderId,
                                                 @Valid @RequestBody QuoteDraftRequest request) {
        return ApiResponse.ok("Quote draft saved", quoteService.saveDraft(orderId, request));
    }

    @PostMapping("/quotes/{quoteId}/approve")
    public ApiResponse<QuoteResponse> approve(@PathVariable String orderId, @PathVariable String quoteId) {
        return ApiResponse.ok("Quote approved", quoteService.approve(orderId, quoteId));
    }

    @PostMapping("/quotes/{quoteId}/pricing-decision")
    public ApiResponse<QuoteResponse> decidePricing(@PathVariable String orderId, @PathVariable String quoteId,
                                                     @Valid @RequestBody PricingApprovalRequest request) {
        return ApiResponse.ok(request.approved() ? "Pricing adjustment approved" : "Pricing adjustment rejected",
                quoteService.decidePricingAdjustment(orderId, quoteId, request));
    }

    @GetMapping("/pricing-assessment")
    public ApiResponse<CustomWorkAssessmentResponse> assessment(@PathVariable String orderId) {
        return ApiResponse.ok(assessmentService.get(orderId));
    }

    @PutMapping("/pricing-assessment")
    public ApiResponse<CustomWorkAssessmentResponse> saveAssessment(@PathVariable String orderId,
            @Valid @RequestBody CustomWorkAssessmentRequest request) {
        return ApiResponse.ok("Custom work assessment saved", assessmentService.save(orderId, request));
    }
}
