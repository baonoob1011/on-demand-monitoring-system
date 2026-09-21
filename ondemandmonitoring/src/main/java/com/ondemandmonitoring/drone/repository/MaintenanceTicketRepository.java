package com.ondemandmonitoring.drone.repository;

import com.ondemandmonitoring.drone.domain.MaintenanceTicket;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MaintenanceTicketRepository extends JpaRepository<MaintenanceTicket, String> {

    /** All tickets for a given device (e.g. drone) — full fault history per device */
    List<MaintenanceTicket> findByDeviceId(String deviceId);

    /** All open tickets assigned to a specific technician user */
    List<MaintenanceTicket> findByAssignedTechnicianId(java.util.UUID technicianId);

    Optional<MaintenanceTicket> findByTicketCode(String ticketCode);
}

