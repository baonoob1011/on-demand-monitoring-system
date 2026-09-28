package com.ondemandmonitoring.device.service;

import com.ondemandmonitoring.device.dto.request.AssignMaintenanceStaffRequest;
import com.ondemandmonitoring.device.dto.request.ResolveMaintenanceTicketRequest;
import com.ondemandmonitoring.device.dto.response.MaintenanceTicketResponse;
import java.util.List;

public interface IMaintenanceTicketService {

    List<MaintenanceTicketResponse> getAllTickets(String status, String deviceId, String staffId);

    MaintenanceTicketResponse getTicketById(String id);

    MaintenanceTicketResponse assignStaff(String id, AssignMaintenanceStaffRequest request);

    MaintenanceTicketResponse resolveTicket(String id, ResolveMaintenanceTicketRequest request);
}

