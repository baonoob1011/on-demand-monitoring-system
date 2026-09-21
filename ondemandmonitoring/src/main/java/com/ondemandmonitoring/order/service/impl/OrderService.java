package com.ondemandmonitoring.order.service.impl;

import com.ondemandmonitoring.categoryservice.domain.CategoryService;
import com.ondemandmonitoring.categoryservice.repository.CategoryServiceRepository;
import com.ondemandmonitoring.common.exception.ApiException;
import com.ondemandmonitoring.common.exception.ErrorCode;
import com.ondemandmonitoring.order.domain.Order;
import com.ondemandmonitoring.order.dto.request.OrderCreateRequest;
import com.ondemandmonitoring.order.dto.response.OrderCreateResponse;
import com.ondemandmonitoring.order.enums.MediaTypeSp;
import com.ondemandmonitoring.order.enums.OrderStatus;
import com.ondemandmonitoring.order.mapper.OrderMapper;
import com.ondemandmonitoring.order.repository.OrderRepository;
import com.ondemandmonitoring.order.service.IOrderService;
import com.ondemandmonitoring.mission.service.IMissionService;
import com.ondemandmonitoring.user.domain.User;
import com.ondemandmonitoring.user.repository.UserRepository;
import com.ondemandmonitoring.user.service.UserIdentityService;
import com.ondemandmonitoring.warehouse.domain.PreferredTime;
import com.ondemandmonitoring.warehouse.repository.PreferredTimeRepository;
import com.ondemandmonitoring.zone.domain.Zone;
import com.ondemandmonitoring.zone.repository.ZoneRepository;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.locationtech.jts.geom.Point;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class OrderService implements IOrderService {

    OrderRepository orderRepository;
    CategoryServiceRepository categoryServiceRepository;
    PreferredTimeRepository preferredTimeRepository;
    ZoneRepository zoneRepository;
    UserRepository userRepository;
    UserIdentityService userIdentityService;
    IMissionService missionService;
    OrderMapper orderMapper;

    @Override
    @Transactional
    public void approveOrder(String orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Order not found: " + orderId));

        if (order.getOrderStatus() != OrderStatus.PENDING) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "Only PENDING orders can be approved");
        }

        order.setOrderStatus(OrderStatus.APPROVED);
        orderRepository.save(order);

        // Flow 2: Create mission for the approved order
        missionService.createMissionForOrder(orderId);
    }

    @Override
    @Transactional
    public OrderCreateResponse createOrder(OrderCreateRequest request) {

        // 1. Resolve Customer from logged-in user session
        User customer = getCurrentAuthenticatedUser();

        // 2. Validate & fetch Category Service
        CategoryService service = categoryServiceRepository.findById(request.getServiceId())
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND,
                        "Category service not found with id: " + request.getServiceId()));

        // 3. Validate & fetch Preferred Time
        PreferredTime preferredTime = preferredTimeRepository.findById(request.getPreferredTimeId())
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND,
                        "Preferred time not found with id: " + request.getPreferredTimeId()));

        // 4. Validate Media Type requirements
        validateMediaType(request);

        // 5. Convert & validate GeoJSON location point inside Zone polygon
        Point point = orderMapper.toPoint(request.getPoint());
        if (point == null) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "Invalid location point coordinates");
        }
        validatePointInsideZone(point);

        // 6. Map and persist order
        Order order = orderMapper.toEntity(request);
        order.setCustomer(customer);
        order.setService(service);
        order.setPreferredTime(preferredTime);
        order.setPoint(point);
        order.setOrderStatus(OrderStatus.PENDING);

        Order savedOrder = orderRepository.save(order);
        return orderMapper.toResponse(savedOrder);
    }

    private User getCurrentAuthenticatedUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new ApiException(ErrorCode.UNAUTHORIZED, "User authentication is required");
        }

        Object principal = auth.getPrincipal();
        if (principal instanceof Jwt jwt) {
            String cognitoSub = jwt.getSubject();
            if (cognitoSub != null && !cognitoSub.isBlank()) {
                try {
                    return userIdentityService.findUserByCognitoSub(cognitoSub);
                } catch (ApiException e) {
                    // Fallback check by email claim
                    String email = jwt.getClaimAsString("email");
                    if (email != null && !email.isBlank()) {
                        return userRepository.findByEmailIgnoreCase(email)
                                .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND,
                                        "User not found for email: " + email));
                    }
                    throw e;
                }
            }
        }

        String name = auth.getName();
        if (name != null && !name.isBlank()) {
            try {
                UUID userId = UUID.fromString(name);
                return userRepository.findById(userId)
                        .orElseThrow(
                                () -> new ApiException(ErrorCode.USER_NOT_FOUND, "User not found with id: " + userId));
            } catch (IllegalArgumentException ignored) {
                return userRepository.findByEmailIgnoreCase(name)
                        .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND,
                                "User not found with identifier: " + name));
            }
        }

        throw new ApiException(ErrorCode.USER_NOT_FOUND, "Authenticated user identity could not be resolved");
    }

    private void validateMediaType(OrderCreateRequest request) {
        if (request.getMediaType() == MediaTypeSp.IMAGE) {
            if (request.getNumberOfPhoto() == null || request.getNumberOfPhoto() <= 0) {
                throw new ApiException(ErrorCode.INVALID_REQUEST,
                        "Number of photo is required and must be greater than 0 when media type is IMAGE");
            }
        } else if (request.getMediaType() == MediaTypeSp.VIDEO) {
            if (request.getDurationOfVideo() == null || request.getDurationOfVideo() <= 0) {
                throw new ApiException(ErrorCode.INVALID_REQUEST,
                        "Duration of video is required and must be greater than 0 when media type is VIDEO");
            }
        }
    }

    private void validatePointInsideZone(Point point) {
        List<Zone> zones = zoneRepository.findAll();
        if (zones.isEmpty()) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "No monitoring zones configured in the system");
        }

        boolean contains = false;
        for (Zone zone : zones) {
            if (zone.getPolygon() != null && (zone.getPolygon().contains(point) || zone.getPolygon().covers(point))) {
                contains = true;
                break;
            }
        }

        if (!contains) {
            throw new ApiException(ErrorCode.INVALID_REQUEST,
                    String.format("Location point [%f, %f] is not within any defined monitoring zone",
                            point.getX(), point.getY()));
        }
    }
}
