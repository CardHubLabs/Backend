package com.kant.cardhub.shipment.application;

import com.kant.cardhub.order.domain.Order;
import com.kant.cardhub.order.domain.OrderStatus;
import com.kant.cardhub.order.infrastructure.OrderRepository;
import com.kant.cardhub.shipment.application.port.ShipmentTrackingPort;
import com.kant.cardhub.shipment.domain.Shipment;
import com.kant.cardhub.shipment.domain.ShipmentEvent;
import com.kant.cardhub.shipment.domain.ShipmentStatus;
import com.kant.cardhub.shipment.infrastructure.ShipmentEventRepository;
import com.kant.cardhub.shipment.infrastructure.ShipmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Service
public class ShipmentTrackingFinalizer {

    private final ShipmentRepository shipmentRepository;
    private final ShipmentEventRepository eventRepository;
    private final OrderRepository orderRepository;
    private final Clock clock;

    public ShipmentTrackingFinalizer(
            ShipmentRepository shipmentRepository,
            ShipmentEventRepository eventRepository,
            OrderRepository orderRepository,
            Clock clock
    ) {
        this.shipmentRepository = shipmentRepository;
        this.eventRepository = eventRepository;
        this.orderRepository = orderRepository;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void apply(UUID shipmentId, ShipmentTrackingPort.TrackingResult result) {
        Shipment snapshot = shipmentRepository.findById(shipmentId)
                .orElseThrow(() -> new IllegalArgumentException("Shipment not found: " + shipmentId));
        if (!snapshot.matchesTracking(result.carrierCode(), result.trackingNumber())) {
            return;
        }
        Order order = null;
        if (result.status() == ShipmentStatus.DELIVERED) {
            order = orderRepository.findByIdForUpdate(snapshot.getOrderId()).orElseThrow();
        }
        Shipment shipment = shipmentRepository.findByIdForUpdate(shipmentId).orElseThrow();
        if (!shipment.matchesTracking(result.carrierCode(), result.trackingNumber())) {
            return;
        }
        Instant now = clock.instant();
        boolean changed = shipment.applyTrackingStatus(result.status(), result.eventAt(), now);
        if (result.providerEventKey() == null) {
            eventRepository.save(ShipmentEvent.create(
                    shipmentId, null, result.status(), result.description(), result.eventAt(), now));
        } else {
            String description = result.description() == null ? "" : result.description();
            eventRepository.insertIgnoringDuplicate(
                    UUID.randomUUID(), shipmentId, result.providerEventKey(), result.status().name(),
                    description.substring(0, Math.min(500, description.length())),
                    result.eventAt() == null ? now : result.eventAt(), now);
        }
        if (changed && shipment.getStatus() == ShipmentStatus.DELIVERED
                && order != null && order.getStatus() == OrderStatus.SHIPPED) {
            order.markDelivered(shipment.getDeliveredAt());
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(UUID shipmentId) {
        shipmentRepository.findByIdForUpdate(shipmentId)
                .ifPresent(shipment -> shipment.trackingFailed(clock.instant()));
    }
}
