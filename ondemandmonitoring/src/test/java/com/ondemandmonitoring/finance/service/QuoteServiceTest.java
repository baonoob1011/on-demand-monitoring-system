package com.ondemandmonitoring.finance.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.ondemandmonitoring.common.exception.ApiException;
import com.ondemandmonitoring.common.exception.ErrorCode;
import com.ondemandmonitoring.finance.config.PaymentProperties;
import com.ondemandmonitoring.finance.domain.*;
import com.ondemandmonitoring.finance.dto.QuoteDraftRequest;
import com.ondemandmonitoring.finance.enums.*;
import com.ondemandmonitoring.finance.repository.*;
import com.ondemandmonitoring.finance.service.impl.QuoteService;
import com.ondemandmonitoring.mission.repository.MissionRepository;
import com.ondemandmonitoring.mission.service.IMissionChecklistExecutionService;
import com.ondemandmonitoring.order.domain.*;
import com.ondemandmonitoring.order.enums.*;
import com.ondemandmonitoring.order.mapper.OrderChecklistItemMapper;
import com.ondemandmonitoring.order.repository.*;
import com.ondemandmonitoring.user.domain.User;
import com.ondemandmonitoring.user.service.AuthenticatedUserResolver;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class QuoteServiceTest {
    OrderRepository orders = mock(OrderRepository.class);
    OrderChecklistItemRepository items = mock(OrderChecklistItemRepository.class);
    QuoteRepository quotes = mock(QuoteRepository.class);
    InvoiceRepository invoices = mock(InvoiceRepository.class);
    MissionRepository missions = mock(MissionRepository.class);
    AuthenticatedUserResolver users = mock(AuthenticatedUserResolver.class);
    IMissionChecklistExecutionService executions = mock(IMissionChecklistExecutionService.class);
    QuoteService service;
    Order order;

    @BeforeEach void setUp() {
        service = new QuoteService(orders, items, mock(OrderChecklistItemMapper.class), quotes, invoices, missions,
                executions, users, new PaymentProperties(new BigDecimal("0.30")));
        order = new Order(); order.setId("o1"); order.setOrderStatus(OrderStatus.PENDING);
        var catalog = new com.ondemandmonitoring.service.domain.Service(); catalog.setName("Building inspection");
        order.setService(catalog);
        when(orders.findByIdForUpdate("o1")).thenReturn(Optional.of(order));
        when(invoices.findByOrderId("o1")).thenReturn(Optional.empty());
        when(quotes.findFirstByOrderIdAndStatusInOrderByQuoteVersionDesc(eq("o1"), any())).thenReturn(Optional.empty());
        when(quotes.countByOrderId("o1")).thenReturn(0);
        when(quotes.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
    }

    @Test void includedDuplicateAndRejectedItemsContributeZero() {
        when(items.findAllByOrderIdOrderByDisplayOrderAscIdAsc("o1")).thenReturn(List.of(
                item("included", ChecklistReviewStatus.INCLUDED), item("duplicate", ChecklistReviewStatus.DUPLICATE),
                item("rejected", ChecklistReviewStatus.REJECTED)));
        var result = service.saveDraft("o1", request("5000000", "200000", "0", Map.of()));
        assertThat(result.additionalAmount()).isEqualByComparingTo("0");
        assertThat(result.totalAmount()).isEqualByComparingTo("4800000");
        assertThat(result.items()).allMatch(line -> line.amount().signum() == 0 || line.type() == QuoteItemType.PACKAGE || line.type() == QuoteItemType.ADJUSTMENT);
    }

    @Test void pricingReviewUsesOrderPriceSnapshotInsteadOfCurrentCatalogPrice() {
        User customer = new User(); customer.setFullName("Customer"); order.setCustomer(customer);
        order.setServiceBasePriceSnapshot(new BigDecimal("2800000"));
        order.getService().setBasePrice(new BigDecimal("9900000"));
        when(orders.findById("o1")).thenReturn(Optional.of(order));
        when(items.findAllByOrderIdOrderByDisplayOrderAscIdAsc("o1")).thenReturn(List.of());
        when(quotes.findHistory("o1")).thenReturn(List.of());

        assertThat(service.getPricingReview("o1").basePackagePrice())
                .isEqualByComparingTo("2800000");
    }

    @Test void additionalItemContributesAndBackendCalculatesTotal() {
        when(items.findAllByOrderIdOrderByDisplayOrderAscIdAsc("o1"))
                .thenReturn(List.of(item("thermal", ChecklistReviewStatus.ADDITIONAL)));
        var result = service.saveDraft("o1", request("5000000", "200000", "0", Map.of("thermal", new BigDecimal("1500000"))));
        assertThat(result.additionalAmount()).isEqualByComparingTo("1500000");
        assertThat(result.totalAmount()).isEqualByComparingTo("6300000");
    }

    @Test void rejectsFractionalVnd() {
        assertThatThrownBy(() -> service.saveDraft("o1", request("1.5", "0", "0", Map.of())))
                .isInstanceOf(ApiException.class).extracting("errorCode").isEqualTo(ErrorCode.QUOTE_PRICING_INVALID);
    }

    @Test void customerCannotAcceptUnapprovedQuote() {
        User customer = new User(); customer.setId("u1"); order.setCustomer(customer); when(users.getCurrentUser()).thenReturn(customer);
        Quote quote = new Quote(); quote.setId("q1"); quote.setOrder(order); quote.setStatus(QuoteStatus.DRAFT);
        when(quotes.findByIdForUpdate("q1")).thenReturn(Optional.of(quote));
        assertThatThrownBy(() -> service.accept("o1", "q1"))
                .isInstanceOf(ApiException.class).extracting("errorCode").isEqualTo(ErrorCode.QUOTE_NOT_APPROVED);
    }

    @Test void acceptingApprovedQuoteCreatesThirtyPercentInvoiceAndWaitingMission() {
        User customer = new User(); customer.setId("u1"); order.setCustomer(customer); when(users.getCurrentUser()).thenReturn(customer);
        var preferred = new com.ondemandmonitoring.warehouse.domain.PreferredTime();
        preferred.setStartTime(java.time.LocalTime.of(8, 0)); preferred.setEndTime(java.time.LocalTime.of(10, 0));
        order.setPreferredTime(preferred); order.setPreferredDateFrom(java.time.LocalDate.of(2026, 10, 10));
        Quote quote = new Quote(); quote.setId("q1"); quote.setOrder(order); quote.setQuoteVersion(1);
        quote.setStatus(QuoteStatus.APPROVED); quote.setTotalAmount(new BigDecimal("8500000"));
        when(quotes.findByIdForUpdate("q1")).thenReturn(Optional.of(quote));
        when(quotes.findFirstByOrderIdAndStatusInOrderByQuoteVersionDesc("o1", List.of(QuoteStatus.APPROVED)))
                .thenReturn(Optional.of(quote));
        when(invoices.save(any())).thenAnswer(call -> call.getArgument(0));
        when(missions.findByOrderIdForUpdate("o1")).thenReturn(Optional.empty());
        when(missions.save(any())).thenAnswer(call -> call.getArgument(0));

        var result = service.accept("o1", "q1");

        assertThat(result.depositAmount()).isEqualByComparingTo("2550000");
        assertThat(result.remainingAmount()).isEqualByComparingTo("8500000");
        assertThat(quote.getStatus()).isEqualTo(QuoteStatus.ACCEPTED_BY_CUSTOMER);
        verify(missions).save(argThat(mission -> mission.getStatus() == com.ondemandmonitoring.mission.enums.MissionStatus.WAITING_DEPOSIT));
        verify(executions).initialize(any());
    }

    private OrderChecklistItem item(String id, ChecklistReviewStatus status) {
        OrderChecklistItem item = new OrderChecklistItem(); item.setId(id); item.setContent(id); item.setReviewStatus(status); return item;
    }
    private QuoteDraftRequest request(String pack, String discount, String adjustment, Map<String, BigDecimal> prices) {
        return new QuoteDraftRequest(new BigDecimal(pack), new BigDecimal(discount), new BigDecimal(adjustment), null, prices);
    }
}
