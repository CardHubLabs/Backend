package com.kant.cardhub.order.infrastructure;

import com.kant.cardhub.order.domain.Order;
import com.kant.cardhub.order.domain.OrderStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from CommerceOrder o where o.id = :id")
    Optional<Order> findByIdForUpdate(@Param("id") UUID id);

    @Query("select o.id from CommerceOrder o where o.status = :status and o.paymentInFlight = false and o.reservationExpiresAt <= :now order by o.reservationExpiresAt")
    List<UUID> findExpirableIds(@Param("status") OrderStatus status, @Param("now") Instant now, Pageable pageable);

    List<Order> findByBuyerIdOrderByCreatedAtDesc(UUID buyerId, Pageable pageable);

    List<Order> findBySellerIdOrderByCreatedAtDesc(UUID sellerId, Pageable pageable);
}
