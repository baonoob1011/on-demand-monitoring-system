package com.ondemandmonitoring.drone.dto.response;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MaintenanceTicketResponse {

    private String id;
    private String ticketCode;
    private String deviceId;
    private String deviceCode;
    private String assignedTechnicianId;
    private String assignedTechnicianName;
    private String reportedBy;
    private String issueType;
    private String severity;
    private String description;
    private String status;
    private String resolutionNotes;
    private Instant openedAt;
    private Instant resolvedAt;
    private Instant closedAt;
}
