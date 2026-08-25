package com.kant.cardhub.shipment.infrastructure;

import com.kant.cardhub.shipment.domain.ShipmentEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface ShipmentEventRepository extends JpaRepository<ShipmentEvent, UUID> {
    boolean existsByShipmentIdAndProviderEventKey(UUID shipmentId, String providerEventKey);
    List<ShipmentEvent> findByShipmentIdOrderByEventAtDesc(UUID shipmentId);

    @Modifying
    @Query(value = """
            insert into shipment_events (
                id, shipment_id, provider_event_key, status, description, event_at, created_at
            ) values (
                :id, :shipmentId, :providerEventKey, :status, :description, :eventAt, :createdAt
            ) on conflict (shipment_id, provider_event_key) do nothing
            """, nativeQuery = true)
    int insertIgnoringDuplicate(
            @Param("id") UUID id,
            @Param("shipmentId") UUID shipmentId,
            @Param("providerEventKey") String providerEventKey,
            @Param("status") String status,
            @Param("description") String description,
            @Param("eventAt") Instant eventAt,
            @Param("createdAt") Instant createdAt);
}
