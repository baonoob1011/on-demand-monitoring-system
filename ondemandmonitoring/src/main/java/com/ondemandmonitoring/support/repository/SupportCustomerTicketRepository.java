package com.ondemandmonitoring.support.repository;

import com.ondemandmonitoring.support.domain.SupportCustomerTicket;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SupportCustomerTicketRepository extends JpaRepository<SupportCustomerTicket, String> {
    List<SupportCustomerTicket> findByCustomerIdOrderByOpenedAtDesc(String customerId);
    List<SupportCustomerTicket> findAllByOrderByOpenedAtDesc();
    List<SupportCustomerTicket> findByStatusOrderByOpenedAtDesc(String status);
}
