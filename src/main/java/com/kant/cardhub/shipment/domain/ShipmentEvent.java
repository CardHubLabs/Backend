package com.kant.cardhub.shipment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "shipment_events")
public class ShipmentEvent {

    @Id
    private UUID id;

    @Column(name = "shipment_id", nullable = false)
    private UUID shipmentId;

    @Column(name = "provider_event_key", length = 200)
    private String providerEventKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private ShipmentStatus status;

    @Column(length = 500)
    private String description;

    @Column(name = "event_at", nullable = false)
    private Instant eventAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ShipmentEvent() {
    }

    public static ShipmentEvent create(
            UUID shipmentId,
            String providerEventKey,
            ShipmentStatus status,
            String description,
            Instant eventAt,
            Instant now
    ) {
        ShipmentEvent event = new ShipmentEvent();
        event.id = UUID.randomUUID();
        event.shipmentId = shipmentId;
        event.providerEventKey = providerEventKey;
        event.status = status;
        event.description = description == null ? "" : description.substring(0, Math.min(500, description.length()));
        event.eventAt = eventAt == null ? now : eventAt;
        event.createdAt = now;
        return event;
    }

    public UUID getId() { return id; }
    public UUID getShipmentId() { return shipmentId; }
    public String getProviderEventKey() { return providerEventKey; }
    public ShipmentStatus getStatus() { return status; }
    public String getDescription() { return description; }
    public Instant getEventAt() { return eventAt; }
}
