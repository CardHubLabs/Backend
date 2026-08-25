package com.kant.cardhub.shipment.infrastructure;

import com.kant.cardhub.shipment.domain.Shipment;
import com.kant.cardhub.shipment.domain.ShipmentStatus;
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

public interface ShipmentRepository extends JpaRepository<Shipment, UUID> {
    Optional<Shipment> findByOrderId(UUID orderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Shipment s where s.id = :id")
    Optional<Shipment> findByIdForUpdate(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Shipment s where s.orderId = :orderId")
    Optional<Shipment> findByOrderIdForUpdate(@Param("orderId") UUID orderId);

    @Query("select s.id from Shipment s where s.status <> :delivered and s.nextCheckAt <= :now order by s.nextCheckAt")
    List<UUID> findTrackingDueIds(@Param("delivered") ShipmentStatus delivered, @Param("now") Instant now, Pageable page);

    @Query("select s.id from Shipment s, CommerceOrder o where o.id = s.orderId and o.status = :orderStatus and s.status = :delivered and s.deliveredAt <= :threshold and s.autoConfirmNoticeSentAt is null order by s.deliveredAt")
    List<UUID> findNoticeDueIds(
            @Param("delivered") ShipmentStatus delivered,
            @Param("orderStatus") OrderStatus orderStatus,
            @Param("threshold") Instant threshold,
            Pageable page);

    @Query("select s.id from Shipment s, CommerceOrder o where o.id = s.orderId and o.status = :orderStatus and s.status = :delivered and s.autoConfirmHeld = false and s.deliveredAt <= :threshold order by s.deliveredAt")
    List<UUID> findAutoConfirmDueIds(
            @Param("delivered") ShipmentStatus delivered,
            @Param("orderStatus") OrderStatus orderStatus,
            @Param("threshold") Instant threshold,
            Pageable page);
}
