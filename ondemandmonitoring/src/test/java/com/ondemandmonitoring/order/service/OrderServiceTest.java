package com.ondemandmonitoring.order.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ondemandmonitoring.common.exception.ApiException;
import com.ondemandmonitoring.mission.service.IMissionService;
import com.ondemandmonitoring.order.domain.Order;
import com.ondemandmonitoring.order.dto.request.OrderCreateRequest;
import com.ondemandmonitoring.order.dto.request.OrderDeliverableRequest;
import com.ondemandmonitoring.order.dto.response.OrderCreateResponse;
import com.ondemandmonitoring.order.enums.OrderStatus;
import com.ondemandmonitoring.order.mapper.OrderMapper;
import org.mapstruct.factory.Mappers;
import com.ondemandmonitoring.order.repository.OrderRepository;
import com.ondemandmonitoring.order.service.impl.OrderService;
import com.ondemandmonitoring.service.domain.DeliverableType;
import com.ondemandmonitoring.service.domain.Service;
import com.ondemandmonitoring.service.repository.DeliverableTypeRepository;
import com.ondemandmonitoring.service.repository.ServiceDeliverableRepository;
import com.ondemandmonitoring.service.repository.ServiceRepository;
import com.ondemandmonitoring.user.domain.User;
import com.ondemandmonitoring.user.service.AuthenticatedUserResolver;
import com.ondemandmonitoring.warehouse.domain.PreferredTime;
import com.ondemandmonitoring.warehouse.repository.PreferredTimeRepository;
import com.ondemandmonitoring.zone.domain.Zone;
import com.ondemandmonitoring.zone.repository.ZoneRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.PrecisionModel;

class OrderServiceTest {

    private OrderRepository orderRepository;
    private ServiceRepository serviceRepository;
    private DeliverableTypeRepository deliverableTypeRepository;
    private ServiceDeliverableRepository serviceDeliverableRepository;
    private PreferredTimeRepository preferredTimeRepository;
    private ZoneRepository zoneRepository;
    private AuthenticatedUserResolver authenticatedUserResolver;
    private OrderMapper orderMapper;
    private OrderService orderService;
    private GeometryFactory geometryFactory;

    @BeforeEach
    void setUp() {
        orderRepository = mock(OrderRepository.class);
        serviceRepository = mock(ServiceRepository.class);
        deliverableTypeRepository = mock(DeliverableTypeRepository.class);
        serviceDeliverableRepository = mock(ServiceDeliverableRepository.class);
        preferredTimeRepository = mock(PreferredTimeRepository.class);
        zoneRepository = mock(ZoneRepository.class);
        authenticatedUserResolver = mock(AuthenticatedUserResolver.class);
        IMissionService missionService = mock(IMissionService.class);
        orderMapper = Mappers.getMapper(OrderMapper.class);
        geometryFactory = new GeometryFactory(new PrecisionModel(), 4326);

        orderService = new OrderService(
                orderRepository,
                serviceRepository,
                deliverableTypeRepository,
                serviceDeliverableRepository,
                preferredTimeRepository,
                zoneRepository,
                authenticatedUserResolver,
                missionService,
                orderMapper
        );

        String userId = UUID.randomUUID().toString();
        User mockUser = User.builder().fullName("Test Customer").email("test@example.com").build();
        mockUser.setId(userId);
        when(authenticatedUserResolver.getCurrentUser()).thenReturn(mockUser);
    }

    private Polygon createSquarePolygon(double minX, double minY, double maxX, double maxY) {
        Coordinate[] coords = new Coordinate[] {
                new Coordinate(minX, minY),
                new Coordinate(maxX, minY),
                new Coordinate(maxX, maxY),
                new Coordinate(minX, maxY),
                new Coordinate(minX, minY)
        };
        Polygon poly = geometryFactory.createPolygon(coords);
        poly.setSRID(4326);
        return poly;
    }

    private Map<String, Object> sampleCoverageAreaMap() {
        return Map.of(
                "type", "Polygon",
                "coordinates", List.of(List.of(
                        List.of(0.0, 0.0),
                        List.of(10.0, 0.0),
                        List.of(10.0, 10.0),
                        List.of(0.0, 10.0),
                        List.of(0.0, 0.0)
                ))
        );
    }

    @Test
    void createOrder_Success() {
        // Arrange
        Service service = Service.builder().name("Land Monitoring").build();
        service.setId("srv-1");
        PreferredTime preferredTime = PreferredTime.builder().name("Morning").build();
        preferredTime.setId("pt-1");
        DeliverableType delType = DeliverableType.builder().name("Photo Map").defaultFormat("JPEG").build();
        delType.setId("dt-1");

        Zone zone = new Zone();
        zone.setPolygon(createSquarePolygon(0.0, 0.0, 10.0, 10.0));

        when(serviceRepository.findById("srv-1")).thenReturn(Optional.of(service));
        when(preferredTimeRepository.findById("pt-1")).thenReturn(Optional.of(preferredTime));
        when(deliverableTypeRepository.findById("dt-1")).thenReturn(Optional.of(delType));
        when(serviceDeliverableRepository.existsByServiceIdAndDeliverableTypeId("srv-1", "dt-1")).thenReturn(true);
        when(zoneRepository.findAll()).thenReturn(List.of(zone));
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
                .longitude(5.0)
                .latitude(5.0)
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
        assertEquals(5.0, response.getLongitude());
        assertEquals(5.0, response.getLatitude());
        assertEquals(300.0, response.getRadiusM());
        assertNotNull(response.getDeliverables());
        assertEquals(1, response.getDeliverables().size());
        assertEquals("dt-1", response.getDeliverables().get(0).getDeliverableTypeId());
    }

    @Test
    void createOrder_ThrowsWhenPointOutsideZone() {
        Service service = Service.builder().name("Land Monitoring").build();
        service.setId("srv-1");
        PreferredTime preferredTime = PreferredTime.builder().name("Morning").build();
        preferredTime.setId("pt-1");
        DeliverableType delType = DeliverableType.builder().name("Photo Map").build();
        delType.setId("dt-1");

        Zone zone = new Zone();
        zone.setPolygon(createSquarePolygon(0.0, 0.0, 10.0, 10.0));

        when(serviceRepository.findById("srv-1")).thenReturn(Optional.of(service));
        when(preferredTimeRepository.findById("pt-1")).thenReturn(Optional.of(preferredTime));
        when(deliverableTypeRepository.findById("dt-1")).thenReturn(Optional.of(delType));
        when(serviceDeliverableRepository.existsByServiceIdAndDeliverableTypeId("srv-1", "dt-1")).thenReturn(true);
        when(zoneRepository.findAll()).thenReturn(List.of(zone));

        OrderCreateRequest request = OrderCreateRequest.builder()
                .title("Outside Point")
                .serviceId("srv-1")
                .preferredTimeId("pt-1")
                .preferredDateFrom(LocalDate.now())
                .preferredDateTo(LocalDate.now().plusDays(2))
                .longitude(20.0)
                .latitude(20.0)
                .coverageArea(sampleCoverageAreaMap())
                .deliverables(List.of(OrderDeliverableRequest.builder().deliverableTypeId("dt-1").requirement(Map.of()).build()))
                .build();

        assertThrows(ApiException.class, () -> orderService.createOrder(request));
    }
}
