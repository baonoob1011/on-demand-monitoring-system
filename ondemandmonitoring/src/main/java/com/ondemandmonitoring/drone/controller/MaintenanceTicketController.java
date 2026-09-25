package com.ondemandmonitoring.drone.controller;

import com.ondemandmonitoring.common.api.ApiResponse;
import com.ondemandmonitoring.drone.dto.request.AssignTechnicianRequest;
import com.ondemandmonitoring.drone.dto.request.ResolveMaintenanceTicketRequest;
import com.ondemandmonitoring.drone.dto.response.MaintenanceTicketResponse;
import com.ondemandmonitoring.drone.service.IMaintenanceTicketService;
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

@Tag(name = "Maintenance Tickets", description = "APIs for managing device maintenance tickets, technician assignment, and repair resolution")
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
            @RequestParam(required = false) String technicianId) {
        List<MaintenanceTicketResponse> tickets = maintenanceTicketService.getAllTickets(status, deviceId, technicianId);
        return ResponseEntity.ok(ApiResponse.ok(tickets));
    }

    @Operation(summary = "Get maintenance ticket by ID", description = "Retrieves details of a maintenance ticket")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<MaintenanceTicketResponse>> getById(@PathVariable String id) {
        MaintenanceTicketResponse response = maintenanceTicketService.getTicketById(id);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @Operation(summary = "Assign technician to ticket", description = "Manager assigns a technician or system operator to resolve the ticket")
    @PatchMapping("/{id}/assign")
    public ResponseEntity<ApiResponse<MaintenanceTicketResponse>> assignTechnician(
            @PathVariable String id,
            @Valid @RequestBody AssignTechnicianRequest request) {
        MaintenanceTicketResponse response = maintenanceTicketService.assignTechnician(id, request);
        return ResponseEntity.ok(ApiResponse.ok("Technician assigned successfully", response));
    }

    @Operation(summary = "Resolve maintenance ticket", description = "Technician completes maintenance, inputs resolution notes, and updates drone status to AVAILABLE")
    @PatchMapping("/{id}/resolve")
    public ResponseEntity<ApiResponse<MaintenanceTicketResponse>> resolveTicket(
            @PathVariable String id,
            @Valid @RequestBody ResolveMaintenanceTicketRequest request) {
        MaintenanceTicketResponse response = maintenanceTicketService.resolveTicket(id, request);
        return ResponseEntity.ok(ApiResponse.ok("Maintenance ticket resolved and drone updated successfully", response));
    }
}
