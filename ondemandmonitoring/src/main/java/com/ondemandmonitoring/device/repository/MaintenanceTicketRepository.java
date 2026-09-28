package com.ondemandmonitoring.device.repository;

import com.ondemandmonitoring.device.domain.MaintenanceTicket;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MaintenanceTicketRepository extends JpaRepository<MaintenanceTicket, String> {

    /** All tickets for a given device — full fault history per device */
    List<MaintenanceTicket> findByDeviceId(String deviceId);

    /** All tickets assigned to a specific staff user */
    List<MaintenanceTicket> findByAssignedStaffId(java.util.UUID staffId);

    Optional<MaintenanceTicket> findByTicketCode(String ticketCode);
}


