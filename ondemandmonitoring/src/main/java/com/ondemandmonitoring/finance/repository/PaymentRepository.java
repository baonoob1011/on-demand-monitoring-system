package com.ondemandmonitoring.finance.repository;

import com.ondemandmonitoring.finance.domain.Payment;
import com.ondemandmonitoring.finance.enums.PaymentStatus;
import com.ondemandmonitoring.finance.enums.PaymentType;
import com.ondemandmonitoring.finance.enums.PaymentProvider;
import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Persistence adapter; locks serialize IPN/QueryDR processing for idempotency. */
public interface PaymentRepository extends JpaRepository<Payment, String> {
    List<Payment> findAllByInvoiceIdOrderByCreatedAtDesc(String invoiceId);
    Optional<Payment> findFirstByInvoiceIdAndPaymentTypeAndProviderAndStatusInOrderByCreatedAtDesc(
            String invoiceId, PaymentType type, PaymentProvider provider, Collection<PaymentStatus> statuses);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p where p.transactionReference=:reference")
    Optional<Payment> findByTransactionReferenceForUpdate(@Param("reference") String reference);

    Optional<Payment> findByTransactionReference(String reference);
    boolean existsByTransactionReference(String reference);
    boolean existsByPaymentCode(Long paymentCode);

    @Query("select coalesce(sum(p.amount), 0) from Payment p where p.invoice.id=:invoiceId and p.status='SUCCESS' and p.paymentType<>'REFUND'")
    BigDecimal sumSuccessfulPayments(@Param("invoiceId") String invoiceId);

}
