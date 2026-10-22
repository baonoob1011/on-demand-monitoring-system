package com.ondemandmonitoring.finance.repository;

import com.ondemandmonitoring.finance.domain.Invoice;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import java.util.List;

public interface InvoiceRepository extends JpaRepository<Invoice, String> {
    Optional<Invoice> findByOrderId(String orderId);
    Optional<Invoice> findByQuoteId(String quoteId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Invoice i where i.id=:id")
    Optional<Invoice> findByIdForUpdate(@Param("id") String id);

    @Query("""
            select count(i) from Invoice i
            where i.status in :openStatuses
              and not exists (
                select p.id from Payment p
                where p.invoice = i and p.paymentType = 'DEPOSIT' and p.status = 'SUCCESS'
              )
            """)
    long countWaitingDeposit(@Param("openStatuses") java.util.Collection<com.ondemandmonitoring.finance.enums.InvoiceStatus> openStatuses);

    @Query("""
            select count(distinct i.id) from Invoice i
            where exists (select p.id from Payment p where p.invoice = i and p.paymentType = 'DEPOSIT' and p.status = 'FAILED')
              and not exists (select p.id from Payment p where p.invoice = i and p.paymentType = 'DEPOSIT' and p.status = 'SUCCESS')
            """)
    long countDepositPaymentIssues();

    @EntityGraph(attributePaths = "order")
    @Query("""
            select distinct i from Invoice i
            where exists (select p.id from Payment p where p.invoice = i and p.paymentType = 'DEPOSIT' and p.status = 'FAILED')
              and not exists (select p.id from Payment p where p.invoice = i and p.paymentType = 'DEPOSIT' and p.status = 'SUCCESS')
            order by i.updatedAt desc
            """)
    List<Invoice> findDepositPaymentIssues(Pageable pageable);
}
