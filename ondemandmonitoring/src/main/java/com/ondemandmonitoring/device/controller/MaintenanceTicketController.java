package com.ondemandmonitoring.device.controller;

import com.ondemandmonitoring.common.api.ApiResponse;
import com.ondemandmonitoring.device.dto.request.AssignMaintenanceStaffRequest;
import com.ondemandmonitoring.device.dto.request.ResolveMaintenanceTicketRequest;
import com.ondemandmonitoring.device.dto.response.MaintenanceTicketResponse;
import com.ondemandmonitoring.device.service.IMaintenanceTicketService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Maintenance Tickets", description = "APIs for managing device maintenance tickets, staff assignment, and repair resolution")
@RestController
@RequestMapping("/api/maintenance-tickets")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class MaintenanceTicketController {

    IMaintenanceTicketService maintenanceTicketService;

    @Operation(summary = "Get all maintenance tickets", description = "Retrieves maintenance tickets with optional status, device, or technician filter")
    @GetMapping
    public ResponseEntity<ApiResponse<List<MaintenanceTicketResponse>>> getAll(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String deviceId,
            @RequestParam(required = false) String staffId) {
        List<MaintenanceTicketResponse> tickets = maintenanceTicketService.getAllTickets(status, deviceId, staffId);
        return ResponseEntity.ok(ApiResponse.ok(tickets));
    }

    @Operation(summary = "Get maintenance ticket by ID", description = "Retrieves details of a maintenance ticket")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<MaintenanceTicketResponse>> getById(@PathVariable String id) {
        MaintenanceTicketResponse response = maintenanceTicketService.getTicketById(id);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @Operation(summary = "Assign staff to ticket", description = "Manager assigns a staff member or system operator to resolve the ticket")
    @PatchMapping("/{id}/assign-staff")
    public ResponseEntity<ApiResponse<MaintenanceTicketResponse>> assignStaff(
            @PathVariable String id,
            @Valid @RequestBody AssignMaintenanceStaffRequest request) {
        MaintenanceTicketResponse response = maintenanceTicketService.assignStaff(id, request);
        return ResponseEntity.ok(ApiResponse.ok("Staff assigned successfully", response));
    }

    @Operation(summary = "Resolve maintenance ticket", description = "Staff completes maintenance, inputs resolution notes, and updates device status to AVAILABLE")
    @PatchMapping("/{id}/resolve")
    public ResponseEntity<ApiResponse<MaintenanceTicketResponse>> resolveTicket(
            @PathVariable String id,
            @Valid @RequestBody ResolveMaintenanceTicketRequest request) {
        MaintenanceTicketResponse response = maintenanceTicketService.resolveTicket(id, request);
        return ResponseEntity.ok(ApiResponse.ok("Maintenance ticket resolved and device updated successfully", response));
    }
}

