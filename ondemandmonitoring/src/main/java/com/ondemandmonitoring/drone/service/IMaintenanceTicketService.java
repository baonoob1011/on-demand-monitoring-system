package com.ondemandmonitoring.drone.service;

import com.ondemandmonitoring.drone.dto.request.AssignTechnicianRequest;
import com.ondemandmonitoring.drone.dto.request.ResolveMaintenanceTicketRequest;
import com.ondemandmonitoring.drone.dto.response.MaintenanceTicketResponse;
import java.util.List;

public interface IMaintenanceTicketService {

    List<MaintenanceTicketResponse> getAllTickets(String status, String deviceId, String technicianId);

    MaintenanceTicketResponse getTicketById(String id);

    MaintenanceTicketResponse assignTechnician(String id, AssignTechnicianRequest request);

    MaintenanceTicketResponse resolveTicket(String id, ResolveMaintenanceTicketRequest request);
}
