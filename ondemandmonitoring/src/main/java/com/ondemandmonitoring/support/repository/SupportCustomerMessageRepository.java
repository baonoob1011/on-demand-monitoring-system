package com.ondemandmonitoring.support.repository;

import com.ondemandmonitoring.support.domain.SupportCustomerMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SupportCustomerMessageRepository extends JpaRepository<SupportCustomerMessage, String> {
    List<SupportCustomerMessage> findByTicketIdOrderByCreatedAtAsc(String ticketId);
}
