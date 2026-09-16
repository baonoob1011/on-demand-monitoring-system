package com.ondemandmonitoring.order.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ondemandmonitoring.categoryservice.domain.CategoryService;
import com.ondemandmonitoring.categoryservice.repository.CategoryServiceRepository;
import com.ondemandmonitoring.common.exception.ApiException;
import com.ondemandmonitoring.order.domain.Order;
import com.ondemandmonitoring.order.dto.GeoJsonPointDto;
import com.ondemandmonitoring.order.dto.request.OrderCreateRequest;
import com.ondemandmonitoring.order.dto.response.OrderCreateResponse;
import com.ondemandmonitoring.order.enums.MediaTypeSp;
import com.ondemandmonitoring.order.enums.OrderStatus;
import com.ondemandmonitoring.order.mapper.OrderMapper;
import com.ondemandmonitoring.order.mapper.OrderMapperImpl;
import com.ondemandmonitoring.order.repository.OrderRepository;
import com.ondemandmonitoring.order.service.impl.OrderService;
import com.ondemandmonitoring.user.domain.User;
import com.ondemandmonitoring.user.repository.UserRepository;
import com.ondemandmonitoring.user.service.UserIdentityService;
import com.ondemandmonitoring.warehouse.domain.PreferredTime;
import com.ondemandmonitoring.warehouse.repository.PreferredTimeRepository;
import com.ondemandmonitoring.zone.domain.Zone;
import com.ondemandmonitoring.zone.repository.ZoneRepository;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class OrderServiceTest {

    private OrderRepository orderRepository;
    private CategoryServiceRepository categoryServiceRepository;
    private PreferredTimeRepository preferredTimeRepository;
    private ZoneRepository zoneRepository;
    private UserRepository userRepository;
    private UserIdentityService userIdentityService;
    private OrderMapper orderMapper;
    private OrderService orderService;
    private GeometryFactory geometryFactory;

    @BeforeEach
    void setUp() {
        orderRepository = mock(OrderRepository.class);
        categoryServiceRepository = mock(CategoryServiceRepository.class);
        preferredTimeRepository = mock(PreferredTimeRepository.class);
        zoneRepository = mock(ZoneRepository.class);
        userRepository = mock(UserRepository.class);
        userIdentityService = mock(UserIdentityService.class);
        orderMapper = new OrderMapperImpl();
        geometryFactory = new GeometryFactory(new PrecisionModel(), 4326);

        orderService = new OrderService(
                orderRepository,
                categoryServiceRepository,
                preferredTimeRepository,
                zoneRepository,
                userRepository,
                userIdentityService,
                orderMapper
        );

        UUID userId = UUID.randomUUID();
        User mockUser = User.builder().id(userId).fullName("Test Customer").email("test@example.com").build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userId.toString(), "credentials", Collections.emptyList())
        );
        when(userRepository.findById(userId)).thenReturn(Optional.of(mockUser));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
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

    @Test
    void createOrder_Success() {
        // Arrange
        CategoryService service = CategoryService.builder().name("Land Monitoring").build();
        service.setId("cs-1");
        PreferredTime preferredTime = PreferredTime.builder().name("Morning").build();
        preferredTime.setId("pt-1");

        Zone zone = new Zone();
        zone.setPolygon(createSquarePolygon(0.0, 0.0, 10.0, 10.0));

        when(categoryServiceRepository.findById("cs-1")).thenReturn(Optional.of(service));
        when(preferredTimeRepository.findById("pt-1")).thenReturn(Optional.of(preferredTime));
        when(zoneRepository.findAll()).thenReturn(List.of(zone));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order o = invocation.getArgument(0);
            o.setId("ord-123");
            return o;
        });

        OrderCreateRequest request = OrderCreateRequest.builder()
                .title("Survey Forest")
                .serviceId("cs-1")
                .preferredTimeId("pt-1")
                .preferredDate(LocalDate.now())
                .mediaType(MediaTypeSp.IMAGE)
                .numberOfPhoto(5)
                .point(GeoJsonPointDto.of(5.0, 5.0))
                .build();

        // Act
        OrderCreateResponse response = orderService.createOrder(request);

        // Assert
        assertNotNull(response);
        assertEquals("ord-123", response.getId());
        assertEquals("Survey Forest", response.getTitle());
        assertEquals(OrderStatus.PENDING, response.getOrderStatus());
        assertEquals(5.0, response.getPoint().getCoordinates().get(0));
        assertEquals(5.0, response.getPoint().getCoordinates().get(1));
    }

    @Test
    void createOrder_ThrowsWhenPointOutsideZone() {
        CategoryService service = CategoryService.builder().name("Land Monitoring").build();
        service.setId("cs-1");
        PreferredTime preferredTime = PreferredTime.builder().name("Morning").build();
        preferredTime.setId("pt-1");

        Zone zone = new Zone();
        zone.setPolygon(createSquarePolygon(0.0, 0.0, 10.0, 10.0));

        when(categoryServiceRepository.findById("cs-1")).thenReturn(Optional.of(service));
        when(preferredTimeRepository.findById("pt-1")).thenReturn(Optional.of(preferredTime));
        when(zoneRepository.findAll()).thenReturn(List.of(zone));

        OrderCreateRequest request = OrderCreateRequest.builder()
                .title("Outside Point")
                .serviceId("cs-1")
                .preferredTimeId("pt-1")
                .preferredDate(LocalDate.now())
                .mediaType(MediaTypeSp.IMAGE)
                .numberOfPhoto(5)
                .point(GeoJsonPointDto.of(20.0, 20.0))
                .build();

        assertThrows(ApiException.class, () -> orderService.createOrder(request));
    }

    @Test
    void createOrder_ThrowsWhenImageMissingPhotos() {
        CategoryService service = CategoryService.builder().name("Land Monitoring").build();
        service.setId("cs-1");
        PreferredTime preferredTime = PreferredTime.builder().name("Morning").build();
        preferredTime.setId("pt-1");

        when(categoryServiceRepository.findById("cs-1")).thenReturn(Optional.of(service));
        when(preferredTimeRepository.findById("pt-1")).thenReturn(Optional.of(preferredTime));

        OrderCreateRequest request = OrderCreateRequest.builder()
                .title("No Photo Count")
                .serviceId("cs-1")
                .preferredTimeId("pt-1")
                .preferredDate(LocalDate.now())
                .mediaType(MediaTypeSp.IMAGE)
                .numberOfPhoto(null)
                .point(GeoJsonPointDto.of(5.0, 5.0))
                .build();

        assertThrows(ApiException.class, () -> orderService.createOrder(request));
    }
}
