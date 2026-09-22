package com.ondemandmonitoring.order.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ondemandmonitoring.common.api.ApiResponse;
import com.ondemandmonitoring.order.dto.GeoJsonPointDto;
import com.ondemandmonitoring.order.dto.request.OrderCreateRequest;
import com.ondemandmonitoring.order.dto.response.OrderCreateResponse;
import com.ondemandmonitoring.order.enums.MediaTypeSp;
import com.ondemandmonitoring.order.enums.OrderStatus;
import com.ondemandmonitoring.order.service.IOrderService;
import java.lang.reflect.Method;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;

class OrderControllerTest {

    private IOrderService orderService;
    private OrderController orderController;

    @BeforeEach
    void setUp() {
        orderService = mock(IOrderService.class);
        orderController = new OrderController(orderService);
    }

    @Test
    @DisplayName("Verify createOrder method is annotated with @PreAuthorize(\"hasRole('CUSTOMER')\")")
    void createOrder_HasPreAuthorizeAnnotationWithCustomerRole() throws NoSuchMethodException {
        Method createOrderMethod = OrderController.class.getMethod("createOrder", OrderCreateRequest.class);
        PreAuthorize preAuthorize = createOrderMethod.getAnnotation(PreAuthorize.class);

        assertNotNull(preAuthorize, "createOrder method should be annotated with @PreAuthorize");
        assertEquals("hasRole('CUSTOMER')", preAuthorize.value(), "Role authorization should be restricted to 'hasRole(\\'CUSTOMER\\')'");
    }

    @Test
    @DisplayName("createOrder delegates to OrderService and returns CREATED response")
    void createOrder_Success_ReturnsCreatedResponse() {
        OrderCreateRequest request = OrderCreateRequest.builder()
                .title("Survey Forest")
                .serviceId("cs-1")
                .preferredTimeId("pt-1")
                .preferredDate(LocalDate.now().plusDays(1))
                .mediaType(MediaTypeSp.IMAGE)
                .numberOfPhoto(5)
                .point(GeoJsonPointDto.of(105.85, 21.02))
                .build();

        OrderCreateResponse mockResponse = OrderCreateResponse.builder()
                .id("ord-123")
                .title("Survey Forest")
                .orderStatus(OrderStatus.PENDING)
                .point(GeoJsonPointDto.of(105.85, 21.02))
                .build();

        when(orderService.createOrder(any(OrderCreateRequest.class))).thenReturn(mockResponse);

        ResponseEntity<ApiResponse<OrderCreateResponse>> responseEntity = orderController.createOrder(request);

        assertNotNull(responseEntity);
        assertEquals(HttpStatus.CREATED, responseEntity.getStatusCode());
        assertNotNull(responseEntity.getBody());
        assertTrue(responseEntity.getBody().isSuccess());
        assertEquals("Order created successfully", responseEntity.getBody().getMessage());
        assertEquals("ord-123", responseEntity.getBody().getData().getId());

        verify(orderService).createOrder(request);
    }
}
