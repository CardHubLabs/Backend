package com.kant.cardhub.payment.infrastructure;

import com.kant.cardhub.payment.domain.Payment;
import com.kant.cardhub.payment.domain.PaymentStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {
    Optional<Payment> findByIdempotencyKey(String idempotencyKey);
    Optional<Payment> findByOrderId(UUID orderId);
    Optional<Payment> findByPaymentKey(String paymentKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p where p.id = :id")
    Optional<Payment> findByIdForUpdate(@Param("id") UUID id);

    @Query("select p.id from Payment p where p.status = :unknown or (p.status = :confirming and p.createdAt <= :staleBefore) order by p.lastReconciledAt nulls first, p.createdAt")
    List<UUID> findRecoverableIds(
            @Param("unknown") PaymentStatus unknown,
            @Param("confirming") PaymentStatus confirming,
            @Param("staleBefore") java.time.Instant staleBefore,
            Pageable pageable);
}
