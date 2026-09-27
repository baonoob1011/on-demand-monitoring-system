package com.ondemandmonitoring.device.service.impl;

import com.ondemandmonitoring.common.exception.ApiException;
import com.ondemandmonitoring.device.domain.Device;
import com.ondemandmonitoring.device.domain.MaintenanceTicket;
import com.ondemandmonitoring.device.dto.request.AssignTechnicianRequest;
import com.ondemandmonitoring.device.dto.request.ResolveMaintenanceTicketRequest;
import com.ondemandmonitoring.device.dto.response.MaintenanceTicketResponse;
import com.ondemandmonitoring.device.enums.DeviceOperationalStatus;
import com.ondemandmonitoring.device.enums.DeviceStatus;
import com.ondemandmonitoring.device.repository.DeviceRepository;
import com.ondemandmonitoring.device.repository.MaintenanceTicketRepository;
import com.ondemandmonitoring.device.service.IMaintenanceTicketService;
import com.ondemandmonitoring.user.domain.User;
import com.ondemandmonitoring.user.repository.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class MaintenanceTicketService implements IMaintenanceTicketService {

    MaintenanceTicketRepository maintenanceTicketRepository;
    DeviceRepository deviceRepository;
    UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public List<MaintenanceTicketResponse> getAllTickets(String status, String deviceId, String technicianId) {
        List<MaintenanceTicket> tickets = maintenanceTicketRepository.findAll();
        return tickets.stream()
                .filter(t -> status == null || status.isBlank() || t.getStatus().equalsIgnoreCase(status))
                .filter(t -> deviceId == null || deviceId.isBlank() || (t.getDevice() != null && t.getDevice().getId().equals(deviceId)))
                .filter(t -> technicianId == null || technicianId.isBlank() || (t.getAssignedTechnician() != null && t.getAssignedTechnician().getId().equals(technicianId)))
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public MaintenanceTicketResponse getTicketById(String id) {
        MaintenanceTicket ticket = maintenanceTicketRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Maintenance ticket not found: " + id));
        return mapToResponse(ticket);
    }

    @Override
    @Transactional
    public MaintenanceTicketResponse assignTechnician(String id, AssignTechnicianRequest request) {
        MaintenanceTicket ticket = maintenanceTicketRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Maintenance ticket not found: " + id));

        User technician = userRepository.findById(request.getTechnicianId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Technician user not found: " + request.getTechnicianId()));

        ticket.setAssignedTechnician(technician);
        ticket.setStatus("IN_PROGRESS");
        MaintenanceTicket saved = maintenanceTicketRepository.save(ticket);
        return mapToResponse(saved);
    }

    @Override
    @Transactional
    public MaintenanceTicketResponse resolveTicket(String id, ResolveMaintenanceTicketRequest request) {
        MaintenanceTicket ticket = maintenanceTicketRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Maintenance ticket not found: " + id));

        ticket.setResolutionNotes(request.getResolutionNotes());
        ticket.setStatus("RESOLVED");
        ticket.setResolvedAt(Instant.now());

        // Update the associated device status back to AVAILABLE (or requested status).
        Device device = ticket.getDevice();
        if (device != null) {
            DeviceOperationalStatus targetStatus = request.getNewDeviceStatus() != null ? request.getNewDeviceStatus() : DeviceOperationalStatus.AVAILABLE;
            device.setOperationalStatus(targetStatus);
            device.setStatus(toDeviceStatus(targetStatus));
            deviceRepository.save(device);
        }

        MaintenanceTicket saved = maintenanceTicketRepository.save(ticket);
        return mapToResponse(saved);
    }

    private MaintenanceTicketResponse mapToResponse(MaintenanceTicket ticket) {
        return MaintenanceTicketResponse.builder()
                .id(ticket.getId())
                .ticketCode(ticket.getTicketCode())
                .deviceId(ticket.getDevice() != null ? ticket.getDevice().getId() : null)
                .deviceCode(ticket.getDevice() != null ? ticket.getDevice().getSerialNumber() : null)
                .assignedTechnicianId(ticket.getAssignedTechnician() != null ? ticket.getAssignedTechnician().getId() : null)
                .assignedTechnicianName(ticket.getAssignedTechnician() != null ? ticket.getAssignedTechnician().getFullName() : null)
                .reportedBy(ticket.getReportedBy())
                .issueType(ticket.getIssueType())
                .severity(ticket.getSeverity())
                .description(ticket.getDescription())
                .status(ticket.getStatus())
                .resolutionNotes(ticket.getResolutionNotes())
                .openedAt(ticket.getOpenedAt())
                .resolvedAt(ticket.getResolvedAt())
                .closedAt(ticket.getClosedAt())
                .build();
    }

    private DeviceStatus toDeviceStatus(DeviceOperationalStatus status) {
        if (status == DeviceOperationalStatus.MAINTENANCE) {
            return DeviceStatus.MAINTENANCE;
        }
        if (status == DeviceOperationalStatus.AVAILABLE || status == DeviceOperationalStatus.IDLE) {
            return DeviceStatus.AVAILABLE;
        }
        return DeviceStatus.IN_USE;
    }
}
