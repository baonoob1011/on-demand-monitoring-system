package com.ondemandmonitoring.order.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ondemandmonitoring.common.exception.ApiException;
import com.ondemandmonitoring.mission.repository.MissionResultRepository;
import com.ondemandmonitoring.order.domain.Order;
import com.ondemandmonitoring.order.dto.request.OrderCreateRequest;
import com.ondemandmonitoring.order.dto.request.OrderDeliverableRequest;
import com.ondemandmonitoring.order.dto.response.OrderCreateResponse;
import com.ondemandmonitoring.order.enums.OrderStatus;
import com.ondemandmonitoring.order.mapper.OrderMapper;
import org.mapstruct.factory.Mappers;
import com.ondemandmonitoring.order.repository.OrderRepository;
import com.ondemandmonitoring.order.service.impl.OrderService;
import com.ondemandmonitoring.order.service.IOrderChecklistSnapshotService;
import com.ondemandmonitoring.service.domain.DeliverableType;
import com.ondemandmonitoring.service.domain.Service;
import com.ondemandmonitoring.service.repository.DeliverableTypeRepository;
import com.ondemandmonitoring.service.repository.ServiceDeliverableRepository;
import com.ondemandmonitoring.service.repository.ServiceRepository;
import com.ondemandmonitoring.user.domain.User;
import com.ondemandmonitoring.user.service.AuthenticatedUserResolver;
import com.ondemandmonitoring.warehouse.domain.PreferredTime;
import com.ondemandmonitoring.warehouse.repository.PreferredTimeRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OrderServiceTest {

    private OrderRepository orderRepository;
    private ServiceRepository serviceRepository;
    private DeliverableTypeRepository deliverableTypeRepository;
    private ServiceDeliverableRepository serviceDeliverableRepository;
    private PreferredTimeRepository preferredTimeRepository;
    private AuthenticatedUserResolver authenticatedUserResolver;
    private OrderMapper orderMapper;
    private MissionResultRepository missionResultRepository;
    private OrderService orderService;

    @BeforeEach
    void setUp() {
        orderRepository = mock(OrderRepository.class);
        serviceRepository = mock(ServiceRepository.class);
        deliverableTypeRepository = mock(DeliverableTypeRepository.class);
        serviceDeliverableRepository = mock(ServiceDeliverableRepository.class);
        preferredTimeRepository = mock(PreferredTimeRepository.class);
        authenticatedUserResolver = mock(AuthenticatedUserResolver.class);
        orderMapper = Mappers.getMapper(OrderMapper.class);
        missionResultRepository = mock(MissionResultRepository.class);

        orderService = new OrderService(
                orderRepository,
                serviceRepository,
                deliverableTypeRepository,
                serviceDeliverableRepository,
                preferredTimeRepository,
                authenticatedUserResolver,
                orderMapper,
                missionResultRepository,
                mock(IOrderChecklistSnapshotService.class)
        );

        String userId = UUID.randomUUID().toString();
        User mockUser = User.builder().fullName("Test Customer").email("test@example.com").build();
        mockUser.setId(userId);
        when(authenticatedUserResolver.getCurrentUser()).thenReturn(mockUser);
    }

    private Map<String, Object> sampleCoverageAreaMap() {
        return Map.of(
                "type", "Polygon",
                "coordinates", List.of(List.of(
                        List.of(106.7000, 10.7760),
                        List.of(106.7010, 10.7760),
                        List.of(106.7010, 10.7770),
                        List.of(106.7000, 10.7770),
                        List.of(106.7000, 10.7760)
                ))
        );
    }

    @Test
    void createOrder_Success() {
        // Arrange
        Service service = Service.builder().name("Land Monitoring")
                .basePrice(new java.math.BigDecimal("2800000")).build();
        service.setId("srv-1");
        PreferredTime preferredTime = PreferredTime.builder().name("Morning").build();
        preferredTime.setId("pt-1");
        DeliverableType delType = DeliverableType.builder().name("Photo Map").defaultFormat("JPEG").build();
        delType.setId("dt-1");

        when(serviceRepository.findByIdForUpdate("srv-1")).thenReturn(Optional.of(service));
        when(preferredTimeRepository.findById("pt-1")).thenReturn(Optional.of(preferredTime));
        when(deliverableTypeRepository.findById("dt-1")).thenReturn(Optional.of(delType));
        when(serviceDeliverableRepository.existsByServiceIdAndDeliverableTypeId("srv-1", "dt-1")).thenReturn(true);
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order o = invocation.getArgument(0);
            o.setId("ord-123");
            return o;
        });

        OrderDeliverableRequest delReq = OrderDeliverableRequest.builder()
                .deliverableTypeId("dt-1")
                .requirement(Map.of("resolution", "4K", "radiusM", 300))
                .build();

        OrderCreateRequest request = OrderCreateRequest.builder()
                .title("Survey Forest")
                .serviceId("srv-1")
                .preferredTimeId("pt-1")
                .preferredDateFrom(LocalDate.now())
                .preferredDateTo(LocalDate.now().plusDays(2))
                .longitude(106.7005)
                .latitude(10.7765)
                .coverageArea(sampleCoverageAreaMap())
                .deliverables(List.of(delReq))
                .build();

        // Act
        OrderCreateResponse response = orderService.createOrder(request);

        // Assert
        assertNotNull(response);
        assertEquals("ord-123", response.getId());
        assertEquals("Survey Forest", response.getTitle());
        assertEquals(OrderStatus.PENDING, response.getOrderStatus());
        assertEquals(new java.math.BigDecimal("2800000"), response.getServiceBasePriceSnapshot());
        assertEquals(106.7005, response.getLongitude());
        assertEquals(10.7765, response.getLatitude());
        assertEquals(300.0, response.getRadiusM());
        assertNotNull(response.getDeliverables());
        assertEquals(1, response.getDeliverables().size());
        assertEquals("dt-1", response.getDeliverables().get(0).getDeliverableTypeId());
    }

    @Test
    void approveOrder_ReturnsApprovedOrderWhenAlreadyApproved() {
        Order order = Order.builder()
                .title("Already approved")
                .orderStatus(OrderStatus.APPROVED)
                .build();
        order.setId("ord-approved");

        when(orderRepository.findById("ord-approved")).thenReturn(Optional.of(order));

        OrderCreateResponse response = orderService.approveOrder("ord-approved");

        assertNotNull(response);
        assertEquals("ord-approved", response.getId());
        assertEquals(OrderStatus.APPROVED, response.getOrderStatus());
        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    void createOrder_ThrowsWhenGpsCoordinatesInvalid() {
        Service service = Service.builder().name("Land Monitoring").build();
        service.setId("srv-1");
        PreferredTime preferredTime = PreferredTime.builder().name("Morning").build();
        preferredTime.setId("pt-1");
        DeliverableType delType = DeliverableType.builder().name("Photo Map").build();
        delType.setId("dt-1");

        when(serviceRepository.findByIdForUpdate("srv-1")).thenReturn(Optional.of(service));
        when(preferredTimeRepository.findById("pt-1")).thenReturn(Optional.of(preferredTime));
        when(deliverableTypeRepository.findById("dt-1")).thenReturn(Optional.of(delType));
        when(serviceDeliverableRepository.existsByServiceIdAndDeliverableTypeId("srv-1", "dt-1")).thenReturn(true);

        OrderCreateRequest request = OrderCreateRequest.builder()
                .title("Outside Point")
                .serviceId("srv-1")
                .preferredTimeId("pt-1")
                .preferredDateFrom(LocalDate.now())
                .preferredDateTo(LocalDate.now().plusDays(2))
                .longitude(106.7)
                .latitude(121.0)
                .coverageArea(sampleCoverageAreaMap())
                .deliverables(List.of(OrderDeliverableRequest.builder().deliverableTypeId("dt-1").requirement(Map.of()).build()))
                .build();

        assertThrows(ApiException.class, () -> orderService.createOrder(request));
    }
}
