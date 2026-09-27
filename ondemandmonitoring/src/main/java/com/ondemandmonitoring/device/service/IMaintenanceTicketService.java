package com.ondemandmonitoring.device.service;

import com.ondemandmonitoring.device.dto.request.AssignTechnicianRequest;
import com.ondemandmonitoring.device.dto.request.ResolveMaintenanceTicketRequest;
import com.ondemandmonitoring.device.dto.response.MaintenanceTicketResponse;
import java.util.List;

public interface IMaintenanceTicketService {

    List<MaintenanceTicketResponse> getAllTickets(String status, String deviceId, String technicianId);

    MaintenanceTicketResponse getTicketById(String id);

    MaintenanceTicketResponse assignTechnician(String id, AssignTechnicianRequest request);

    MaintenanceTicketResponse resolveTicket(String id, ResolveMaintenanceTicketRequest request);
}
