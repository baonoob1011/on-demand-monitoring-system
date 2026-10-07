package com.ondemandmonitoring.finance.service.impl;

import com.ondemandmonitoring.common.exception.ApiException;
import com.ondemandmonitoring.common.exception.ErrorCode;
import com.ondemandmonitoring.finance.config.PaymentProperties;
import com.ondemandmonitoring.finance.domain.*;
import com.ondemandmonitoring.finance.dto.*;
import com.ondemandmonitoring.finance.enums.*;
import com.ondemandmonitoring.finance.repository.InvoiceRepository;
import com.ondemandmonitoring.finance.repository.QuoteRepository;
import com.ondemandmonitoring.finance.mapper.FinanceMapper;
import com.ondemandmonitoring.finance.service.IQuoteService;
import com.ondemandmonitoring.mission.domain.Mission;
import com.ondemandmonitoring.mission.enums.MissionStatus;
import com.ondemandmonitoring.mission.repository.MissionRepository;
import com.ondemandmonitoring.mission.service.IMissionChecklistExecutionService;
import com.ondemandmonitoring.order.domain.Order;
import com.ondemandmonitoring.order.domain.OrderChecklistItem;
import com.ondemandmonitoring.order.dto.response.OrderChecklistItemResponse;
import com.ondemandmonitoring.order.enums.ChecklistReviewStatus;
import com.ondemandmonitoring.order.enums.OrderStatus;
import com.ondemandmonitoring.order.mapper.OrderChecklistItemMapper;
import com.ondemandmonitoring.order.repository.OrderChecklistItemRepository;
import com.ondemandmonitoring.order.repository.OrderRepository;
import com.ondemandmonitoring.user.domain.User;
import com.ondemandmonitoring.user.service.AuthenticatedUserResolver;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class QuoteService implements IQuoteService {
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(0);
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private final OrderRepository orders;
    private final OrderChecklistItemRepository checklistItems;
    private final OrderChecklistItemMapper checklistMapper;
    private final QuoteRepository quotes;
    private final InvoiceRepository invoices;
    private final MissionRepository missions;
    private final IMissionChecklistExecutionService checklistExecutionService;
    private final AuthenticatedUserResolver currentUser;
    private final PaymentProperties paymentProperties;

    @Transactional(readOnly = true)
    public PricingReviewResponse getPricingReview(String orderId) {
        Order order = orders.findById(orderId).orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));
        List<OrderChecklistItemResponse> items = checklistItems.findAllByOrderIdOrderByDisplayOrderAscIdAsc(orderId)
                .stream().map(checklistMapper::toResponse).toList();
        List<QuoteResponse> history = quotes.findHistory(orderId).stream().map(FinanceMapper::quote).toList();
        QuoteResponse current = history.stream().filter(q -> q.status() == QuoteStatus.DRAFT
                || q.status() == QuoteStatus.APPROVED || q.status() == QuoteStatus.PENDING_REVIEW).findFirst().orElse(null);
        BigDecimal basePackagePrice = order.getServiceBasePriceSnapshot() != null
                ? order.getServiceBasePriceSnapshot()
                : order.getService().getBasePrice();
        return new PricingReviewResponse(orderId, order.getOrderCode(), order.getCustomer().getFullName(),
                order.getService().getName(), basePackagePrice, items, current, history);
    }

    @Transactional
    public OrderChecklistItemResponse reviewChecklist(String orderId, String itemId, ChecklistReviewRequest request) {
        orders.findByIdForUpdate(orderId).orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));
        OrderChecklistItem item = checklistItems.findByIdAndOrderId(itemId, orderId)
                .orElseThrow(() -> new ApiException(ErrorCode.CHECKLIST_NOT_FOUND));
        if (request.status() == ChecklistReviewStatus.PENDING) {
            throw new ApiException(ErrorCode.ORDER_CHECKLIST_REVIEW_INVALID, "Manager must choose a final review status");
        }
        item.setReviewStatus(request.status());
        item.setManagerNote(trimToNull(request.managerNote()));
        return checklistMapper.toResponse(item);
    }

    @Transactional
    public QuoteResponse saveDraft(String orderId, QuoteDraftRequest request) {
        Order order = orders.findByIdForUpdate(orderId).orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));
        if (order.getOrderStatus() == OrderStatus.REJECTED || order.getOrderStatus() == OrderStatus.CANCELLED
                || invoices.findByOrderId(orderId).isPresent()) {
            throw new ApiException(ErrorCode.QUOTE_PRICING_INVALID, "Order can no longer be priced");
        }
        List<OrderChecklistItem> reviewed = checklistItems.findAllByOrderIdOrderByDisplayOrderAscIdAsc(orderId);
        validateMoney(request.packagePrice(), "packagePrice", false);
        validateMoney(request.discountAmount(), "discountAmount", false);
        validateMoney(request.adjustmentAmount(), "adjustmentAmount", true);

        Optional<Quote> active = quotes.findFirstByOrderIdAndStatusInOrderByQuoteVersionDesc(orderId,
                List.of(QuoteStatus.DRAFT, QuoteStatus.PENDING_REVIEW, QuoteStatus.APPROVED));
        Quote quote;
        if (active.isPresent() && active.get().getStatus() == QuoteStatus.DRAFT) {
            quote = quotes.findByIdForUpdate(active.get().getId()).orElseThrow();
        } else {
            active.ifPresent(previous -> {
                previous.setStatus(QuoteStatus.SUPERSEDED);
                quotes.saveAndFlush(previous);
            });
            quote = new Quote();
            quote.setOrder(order);
            quote.setQuoteVersion(quotes.countByOrderId(orderId) + 1);
            quote.setStatus(QuoteStatus.DRAFT);
        }

        List<QuoteItem> lines = buildLines(order, reviewed, request);
        BigDecimal additional = lines.stream().filter(line -> line.getType() == QuoteItemType.ADDITIONAL)
                .map(QuoteItem::getAmount).reduce(ZERO, BigDecimal::add);
        BigDecimal total = request.packagePrice().add(additional).subtract(request.discountAmount())
                .add(request.adjustmentAmount()).setScale(0, RoundingMode.UNNECESSARY);
        if (total.signum() <= 0) throw new ApiException(ErrorCode.QUOTE_PRICING_INVALID, "Quote total must be positive");

        quote.setPackagePrice(vnd(request.packagePrice()));
        quote.setAdditionalAmount(additional);
        quote.setDiscountAmount(vnd(request.discountAmount()));
        quote.setAdjustmentAmount(vnd(request.adjustmentAmount()));
        quote.setTotalAmount(total);
        quote.setManagerNote(trimToNull(request.managerNote()));
        quote.replaceItems(lines);
        return FinanceMapper.quote(quotes.saveAndFlush(quote));
    }

    @Transactional
    public QuoteResponse approve(String orderId, String quoteId) {
        Order order = orders.findByIdForUpdate(orderId).orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));
        Quote quote = quotes.findByIdForUpdate(quoteId).orElseThrow(() -> new ApiException(ErrorCode.QUOTE_NOT_FOUND));
        if (!quote.getOrder().getId().equals(orderId) || quote.getStatus() != QuoteStatus.DRAFT) {
            throw new ApiException(ErrorCode.QUOTE_NOT_CURRENT);
        }
        if (checklistItems.findAllByOrderIdOrderByDisplayOrderAscIdAsc(orderId).stream()
                .anyMatch(item -> item.getReviewStatus() == null || item.getReviewStatus() == ChecklistReviewStatus.PENDING)) {
            throw new ApiException(ErrorCode.ORDER_CHECKLIST_REVIEW_INVALID);
        }
        quote.setStatus(QuoteStatus.APPROVED);
        quote.setApprovedBy(currentUser.getCurrentUser());
        quote.setApprovedAt(Instant.now());
        if (order.getOrderStatus() == OrderStatus.PENDING) order.setOrderStatus(OrderStatus.APPROVED);
        return FinanceMapper.quote(quote);
    }

    @Transactional(readOnly = true)
    public QuoteResponse getCurrentForCustomer(String orderId) {
        assertOwner(orderId);
        return quotes.findFirstByOrderIdAndStatusInOrderByQuoteVersionDesc(orderId,
                        List.of(QuoteStatus.APPROVED, QuoteStatus.ACCEPTED_BY_CUSTOMER))
                .map(FinanceMapper::quote).orElseThrow(() -> new ApiException(ErrorCode.QUOTE_NOT_FOUND));
    }

    @Transactional
    public InvoiceResponse accept(String orderId, String quoteId) {
        Order order = orders.findByIdForUpdate(orderId).orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));
        assertOwner(order);
        Quote quote = quotes.findByIdForUpdate(quoteId).orElseThrow(() -> new ApiException(ErrorCode.QUOTE_NOT_FOUND));
        if (!quote.getOrder().getId().equals(orderId)) throw new ApiException(ErrorCode.QUOTE_NOT_CURRENT);
        if (quote.getStatus() == QuoteStatus.ACCEPTED_BY_CUSTOMER) {
            return invoices.findByQuoteId(quoteId).map(this::invoiceResponse)
                    .orElseThrow(() -> new ApiException(ErrorCode.INVOICE_NOT_FOUND));
        }
        Quote current = quotes.findFirstByOrderIdAndStatusInOrderByQuoteVersionDesc(orderId, List.of(QuoteStatus.APPROVED))
                .orElseThrow(() -> new ApiException(ErrorCode.QUOTE_NOT_APPROVED));
        if (!current.getId().equals(quoteId)) throw new ApiException(ErrorCode.QUOTE_NOT_CURRENT);

        quote.setStatus(QuoteStatus.ACCEPTED_BY_CUSTOMER);
        quote.setAcceptedAt(Instant.now());
        Invoice invoice = new Invoice();
        invoice.setInvoiceNumber("INV-" + LocalDate.now(BUSINESS_ZONE).getYear() + "-"
                + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        invoice.setOrder(order);
        invoice.setQuote(quote);
        invoice.setTotalAmount(quote.getTotalAmount());
        invoice.setDepositAmount(quote.getTotalAmount().multiply(paymentProperties.depositRate()).setScale(0, RoundingMode.HALF_UP));
        invoice.setPaidAmount(ZERO);
        invoice.setRemainingAmount(quote.getTotalAmount());
        invoice.setStatus(InvoiceStatus.ISSUED);
        invoice.setIssuedAt(Instant.now());
        Invoice saved = invoices.save(invoice);
        ensureWaitingDepositMission(order);
        return invoiceResponse(saved);
    }

    @Transactional(readOnly = true)
    public InvoiceResponse getInvoiceByOrder(String orderId) {
        assertOwnerOrManager(orderId);
        return invoices.findByOrderId(orderId).map(this::invoiceResponse)
                .orElseThrow(() -> new ApiException(ErrorCode.INVOICE_NOT_FOUND));
    }

    private InvoiceResponse invoiceResponse(Invoice invoice) {
        boolean finalPaymentAllowed = missions.findByOrderId(invoice.getOrder().getId())
                .map(mission -> mission.getStatus() == MissionStatus.COMPLETED)
                .orElse(false);
        return FinanceMapper.invoice(invoice, finalPaymentAllowed);
    }

    private List<QuoteItem> buildLines(Order order, List<OrderChecklistItem> reviewed, QuoteDraftRequest request) {
        List<QuoteItem> lines = new ArrayList<>();
        lines.add(line(QuoteItemType.PACKAGE, order.getService().getName(), request.packagePrice(), null, lines.size()));
        Map<String, BigDecimal> prices = request.additionalUnitPrices() == null ? Map.of() : request.additionalUnitPrices();
        for (OrderChecklistItem item : reviewed) {
            ChecklistReviewStatus status = item.getReviewStatus() == null ? ChecklistReviewStatus.PENDING : item.getReviewStatus();
            if (status == ChecklistReviewStatus.PENDING) continue;
            BigDecimal price = ZERO;
            QuoteItemType type = QuoteItemType.INCLUDED;
            if (status == ChecklistReviewStatus.ADDITIONAL) {
                price = prices.get(item.getId());
                validateMoney(price, "additionalUnitPrices[" + item.getId() + "]", false);
                type = QuoteItemType.ADDITIONAL;
            }
            QuoteItem line = line(type, item.getContent(), price, item, lines.size());
            lines.add(line);
        }
        if (request.discountAmount().signum() > 0) {
            lines.add(line(QuoteItemType.ADJUSTMENT, "Discount", request.discountAmount().negate(), null, lines.size()));
        }
        if (request.adjustmentAmount().signum() != 0) {
            lines.add(line(QuoteItemType.ADJUSTMENT, "Manager adjustment", request.adjustmentAmount(), null, lines.size()));
        }
        return lines;
    }

    private QuoteItem line(QuoteItemType type, String description, BigDecimal unitPrice,
                           OrderChecklistItem checklistItem, int displayOrder) {
        QuoteItem line = new QuoteItem();
        line.setType(type); line.setDescription(description); line.setQuantity(BigDecimal.ONE);
        line.setUnitPrice(vnd(unitPrice)); line.setAmount(vnd(unitPrice));
        line.setOrderChecklistItem(checklistItem); line.setDisplayOrder(displayOrder);
        return line;
    }

    private void ensureWaitingDepositMission(Order order) {
        Mission mission = missions.findByOrderIdForUpdate(order.getId()).orElse(null);
        if (mission == null) {
            mission = new Mission();
            mission.setOrder(order);
            mission.setMissionCode(generateMissionCode());
            LocalDate date = Optional.ofNullable(order.getPreferredDateFrom()).orElse(LocalDate.now(BUSINESS_ZONE));
            LocalTime start = order.getPreferredTime().getStartTime();
            LocalTime end = order.getPreferredTime().getEndTime();
            mission.setScheduledStartAt(ZonedDateTime.of(date, start, BUSINESS_ZONE).toInstant());
            mission.setScheduledEndAt(ZonedDateTime.of(end.isAfter(start) ? date : date.plusDays(1), end, BUSINESS_ZONE).toInstant());
            mission.setStatus(MissionStatus.WAITING_DEPOSIT);
            mission = missions.save(mission);
            checklistExecutionService.initialize(mission);
        } else if (mission.getStatus() == MissionStatus.CREATED || mission.getStatus() == MissionStatus.RESOURCE_ASSIGNING) {
            mission.setStatus(MissionStatus.WAITING_DEPOSIT);
        } else if (mission.getStatus() != MissionStatus.WAITING_DEPOSIT) {
            throw new ApiException(ErrorCode.MISSION_STATUS_INVALID, "Existing mission has already entered execution");
        }
    }

    private String generateMissionCode() {
        String code;
        do code = "MSN-" + LocalDate.now(BUSINESS_ZONE).getYear() + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        while (missions.existsByMissionCode(code));
        return code;
    }

    private void assertOwner(String orderId) {
        assertOwner(orders.findById(orderId).orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND)));
    }

    private void assertOwner(Order order) {
        User user = currentUser.getCurrentUser();
        if (order.getCustomer() == null || !order.getCustomer().getId().equals(user.getId())) throw new ApiException(ErrorCode.ACCESS_DENIED);
    }

    private void assertOwnerOrManager(String orderId) {
        Order order = orders.findById(orderId).orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));
        User user = currentUser.getCurrentUser();
        boolean manager = user.getRole() != null && (user.getRole().getCode().name().equals("MANAGER") || user.getRole().getCode().name().equals("ADMIN"));
        if (!manager) assertOwner(order);
    }

    private void validateMoney(BigDecimal value, String field, boolean signed) {
        if (value == null || (!signed && value.signum() < 0) || value.stripTrailingZeros().scale() > 0) {
            throw new ApiException(ErrorCode.QUOTE_PRICING_INVALID, field + " must be an integer VND amount" );
        }
    }

    private BigDecimal vnd(BigDecimal value) { return value.setScale(0, RoundingMode.UNNECESSARY); }
    private String trimToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}
