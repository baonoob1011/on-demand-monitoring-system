package com.ondemandmonitoring.manager.controller;

import com.ondemandmonitoring.common.api.ApiResponse;
import com.ondemandmonitoring.device.domain.Device;
import com.ondemandmonitoring.device.enums.DeviceStatus;
import com.ondemandmonitoring.device.repository.DeviceRepository;
import com.ondemandmonitoring.mission.domain.Mission;
import com.ondemandmonitoring.mission.enums.MissionStatus;
import com.ondemandmonitoring.mission.repository.MissionRepository;
import com.ondemandmonitoring.order.domain.Order;
import com.ondemandmonitoring.order.enums.OrderStatus;
import com.ondemandmonitoring.order.repository.OrderRepository;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.EnumMap;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/manager/dashboard")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
public class ManagerDashboardController {

    OrderRepository orderRepository;
    MissionRepository missionRepository;
    DeviceRepository deviceRepository;

    @GetMapping
    @Transactional(readOnly = true)
    public ResponseEntity<ApiResponse<Map<String, Object>>> getDashboard() {
        var orders = orderRepository.findAll();
        var missions = missionRepository.findAll();
        var devices = deviceRepository.findAll();

        long pendingOrders = countOrders(orders, OrderStatus.PENDING);
        long missionsToday = countMissionsToday(missions);
        long missionsInFlight = countMissions(missions, MissionStatus.IN_FLIGHT)
                + countMissions(missions, MissionStatus.IN_PROGRESS);
        long readyDevices = countDevices(devices, DeviceStatus.AVAILABLE);
        long maintenanceDevices = countDevices(devices, DeviceStatus.MAINTENANCE);
        long unassignedMissions = missions.stream()
                .filter(mission -> mission.getStatus() == MissionStatus.CREATED
                        || mission.getStatus() == MissionStatus.RESOURCE_ASSIGNING)
                .count();

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("kpis", Map.of(
                "pendingOrders", Map.of("count", pendingOrders, "detail", pendingOrders + " đơn chờ duyệt"),
                "missionsToday", Map.of("count", missionsToday, "detail", "Mission trong hôm nay"),
                "missionsInFlight", Map.of("count", missionsInFlight, "detail", ""),
                "dronesReady", Map.of("ready", readyDevices, "total", devices.size(), "detail", maintenanceDevices + " bảo trì"),
                "actionItems", Map.of("count", pendingOrders + unassignedMissions, "detail", "Cần xử lý")));
        data.put("missionStatusByDay", missionStatusByDay(missions));
        data.put("droneStatusBreakdown", deviceStatusBreakdown(devices));
        data.put("actionItems", actionItems(orders, missions));
        data.put("flyingMission", null);
        data.put("navCounts", Map.of(
                "pendingOrders", pendingOrders,
                "openMaintenanceTickets", maintenanceDevices,
                "mediaNeedsAction", 0));

        data.put("totalOrders", orders.size());
        data.put("approvedOrders", countOrders(orders, OrderStatus.APPROVED));
        data.put("totalMissions", missions.size());
        data.put("activeMissions", missions.stream().filter(this::isActiveMission).count());
        data.put("totalDevices", devices.size());
        data.put("availableDevices", readyDevices);

        return ResponseEntity.ok(ApiResponse.ok(data));
    }

    private long countOrders(java.util.List<Order> orders, OrderStatus status) {
        return orders.stream().filter(order -> order.getOrderStatus() == status).count();
    }

    private long countDevices(java.util.List<Device> devices, DeviceStatus status) {
        return devices.stream().filter(device -> device.getStatus() == status).count();
    }

    private long countMissions(java.util.List<Mission> missions, MissionStatus status) {
        return missions.stream().filter(mission -> mission.getStatus() == status).count();
    }

    private long countMissionsToday(java.util.List<Mission> missions) {
        ZoneId zone = ZoneId.of("Asia/Ho_Chi_Minh");
        LocalDate today = LocalDate.now(zone);
        return missions.stream()
                .filter(mission -> mission.getScheduledStartAt() != null
                        && mission.getScheduledStartAt().atZone(zone).toLocalDate().equals(today))
                .count();
    }

    private boolean isActiveMission(Mission mission) {
        return mission.getStatus() != MissionStatus.COMPLETED
                && mission.getStatus() != MissionStatus.FAILED
                && mission.getStatus() != MissionStatus.CANCELLED;
    }

    private List<Map<String, Object>> missionStatusByDay(java.util.List<Mission> missions) {
        ZoneId zone = ZoneId.of("Asia/Ho_Chi_Minh");
        LocalDate today = LocalDate.now(zone);
        return java.util.stream.IntStream.rangeClosed(0, 6)
                .mapToObj(offset -> {
                    LocalDate day = today.minusDays(6L - offset);
                    List<Mission> dayMissions = missions.stream()
                            .filter(mission -> mission.getScheduledStartAt() != null
                                    && mission.getScheduledStartAt().atZone(zone).toLocalDate().equals(day))
                            .toList();
                    Map<String, Object> point = new LinkedHashMap<>();
                    point.put("date", day.toString());
                    point.put("completed", countMissions(dayMissions, MissionStatus.COMPLETED));
                    point.put("inFlight", countMissions(dayMissions, MissionStatus.IN_FLIGHT)
                            + countMissions(dayMissions, MissionStatus.IN_PROGRESS));
                    point.put("failed", countMissions(dayMissions, MissionStatus.FAILED));
                    point.put("cancelled", countMissions(dayMissions, MissionStatus.CANCELLED));
                    return point;
                })
                .toList();
    }

    private List<Map<String, Object>> deviceStatusBreakdown(java.util.List<Device> devices) {
        Map<DeviceStatus, Long> counts = new EnumMap<>(DeviceStatus.class);
        devices.forEach(device -> counts.merge(device.getStatus(), 1L, Long::sum));
        return counts.entrySet().stream()
                .map(entry -> Map.<String, Object>of("status", entry.getKey().name(), "count", entry.getValue()))
                .toList();
    }

    private List<Map<String, Object>> actionItems(java.util.List<Order> orders, java.util.List<Mission> missions) {
        List<Map<String, Object>> orderItems = orders.stream()
                .filter(order -> order.getOrderStatus() == OrderStatus.PENDING)
                .limit(5)
                .map(order -> Map.<String, Object>of(
                        "id", "order-" + order.getId(),
                        "type", "ORDER_PENDING",
                        "orderId", order.getId(),
                        "code", order.getId(),
                        "title", order.getTitle() == null ? "Đơn chờ duyệt" : order.getTitle(),
                        "subtitle", order.getCustomer() == null ? "Khách hàng" : order.getCustomer().getFullName(),
                        "submittedAtIso", order.getCreatedAt() == null ? java.time.Instant.now().toString() : order.getCreatedAt().toString()))
                .toList();

        List<Map<String, Object>> missionItems = missions.stream()
                .filter(mission -> mission.getStatus() == MissionStatus.CREATED
                        || mission.getStatus() == MissionStatus.RESOURCE_ASSIGNING)
                .limit(5)
                .map(mission -> Map.<String, Object>of(
                        "id", "mission-" + mission.getId(),
                        "type", "MISSION_UNASSIGNED",
                        "missionId", mission.getId(),
                        "code", mission.getMissionCode() == null ? mission.getId() : mission.getMissionCode(),
                        "title", "Mission chờ phân công",
                        "subtitle", mission.getOrder() == null ? "Cần gán thiết bị và staff" : mission.getOrder().getTitle(),
                        "scheduledStartIso", mission.getScheduledStartAt() == null ? java.time.Instant.now().toString() : mission.getScheduledStartAt().toString()))
                .toList();

        return java.util.stream.Stream.concat(orderItems.stream(), missionItems.stream()).toList();
    }
}
