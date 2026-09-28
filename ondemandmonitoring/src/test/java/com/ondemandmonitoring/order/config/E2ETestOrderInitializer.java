package com.ondemandmonitoring.order.config;

import com.ondemandmonitoring.order.domain.Order;
import com.ondemandmonitoring.order.enums.OrderStatus;
import com.ondemandmonitoring.order.repository.OrderRepository;
import com.ondemandmonitoring.service.domain.Service;
import com.ondemandmonitoring.service.repository.ServiceRepository;
import com.ondemandmonitoring.user.domain.User;
import com.ondemandmonitoring.user.repository.UserRepository;
import com.ondemandmonitoring.warehouse.domain.PreferredTime;
import com.ondemandmonitoring.warehouse.enums.PreferredTimeCode;
import com.ondemandmonitoring.warehouse.repository.PreferredTimeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalTime;

@Component
@org.springframework.core.annotation.Order(100)
@RequiredArgsConstructor
@Slf4j
public class E2ETestOrderInitializer implements ApplicationRunner {

    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final ServiceRepository serviceRepository;
    private final PreferredTimeRepository preferredTimeRepository;

    @Override
    public void run(ApplicationArguments args) {
        if (orderRepository.count() == 0) {
            User customer = userRepository.findAll().stream()
                    .filter(u -> u.getEmail() != null && u.getEmail().contains("customer"))
                    .findFirst()
                    .or(() -> userRepository.findAll().stream().findFirst())
                    .orElse(null);

            Service service = serviceRepository.findAll().stream()
                    .findFirst()
                    .orElse(null);

            PreferredTime preferredTime = preferredTimeRepository.findAll().stream()
                    .findFirst()
                    .orElseGet(() -> preferredTimeRepository.save(PreferredTime.builder()
                            .code(PreferredTimeCode.MORNING)
                            .name("Buổi sáng (08:00 - 12:00)")
                            .startTime(LocalTime.of(8, 0))
                            .endTime(LocalTime.of(12, 0))
                            .build()));

            if (customer != null && service != null) {
                Order order = new Order();
                order.setCustomer(customer);
                order.setService(service);
                order.setPreferredTime(preferredTime);
                order.setTitle("E2E_SEED_ORDER");
                order.setDescription("Seed order for E2E testing");
                order.setAddress("LOCAL_SIMULATION_METERS_GAZEBO_XY");
                GeometryFactory factory = new GeometryFactory(new PrecisionModel(), 4326);
                order.setPoint(factory.createPoint(new Coordinate(0.0, -280.0)));
                order.setPreferredDateFrom(LocalDate.now());
                order.setPreferredDateTo(LocalDate.now().plusDays(7));
                order.setOrderStatus(OrderStatus.APPROVED);
                orderRepository.save(order);
                log.info("E2ETestOrderInitializer: seeded test order for E2E tests");
            }
        }
    }
}
