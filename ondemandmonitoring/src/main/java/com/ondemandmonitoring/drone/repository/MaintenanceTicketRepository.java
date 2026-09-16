package com.ondemandmonitoring.drone.repository;

import com.ondemandmonitoring.drone.domain.MaintenanceTicket;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MaintenanceTicketRepository extends JpaRepository<MaintenanceTicket, String> {

    List<MaintenanceTicket> findByDroneId(String droneId);

    Optional<MaintenanceTicket> findByTicketCode(String ticketCode);
}
