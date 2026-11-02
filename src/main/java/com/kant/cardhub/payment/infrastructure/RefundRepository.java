package com.kant.cardhub.payment.infrastructure;

import com.kant.cardhub.payment.domain.Refund;
import com.kant.cardhub.payment.domain.RefundStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RefundRepository extends JpaRepository<Refund, UUID> {
    Optional<Refund> findByOrderId(UUID orderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Refund r where r.orderId = :orderId")
    Optional<Refund> findByOrderIdForUpdate(@Param("orderId") UUID orderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Refund r where r.id = :id")
    Optional<Refund> findByIdForUpdate(@Param("id") UUID id);

    @Query("select r.id from Refund r where r.status = :unknown or (r.status = :processing and r.updatedAt <= :staleBefore) order by r.updatedAt")
    List<UUID> findRecoverableIds(
            @Param("unknown") RefundStatus unknown,
            @Param("processing") RefundStatus processing,
            @Param("staleBefore") java.time.Instant staleBefore,
            Pageable pageable);
}
