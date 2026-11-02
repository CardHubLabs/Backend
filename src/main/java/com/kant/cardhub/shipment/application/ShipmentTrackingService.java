package com.kant.cardhub.shipment.application;

import com.kant.cardhub.shipment.application.port.ShipmentTrackingPort;
import com.kant.cardhub.shipment.domain.Shipment;
import com.kant.cardhub.shipment.domain.ShipmentStatus;
import com.kant.cardhub.shipment.infrastructure.ShipmentRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class ShipmentTrackingService {

    private final ShipmentRepository shipmentRepository;
    private final ShipmentTrackingPort trackingPort;
    private final ShipmentTrackingFinalizer finalizer;
    private final Clock clock;

    public ShipmentTrackingService(
            ShipmentRepository shipmentRepository,
            ShipmentTrackingPort trackingPort,
            ShipmentTrackingFinalizer finalizer,
            Clock clock
    ) {
        this.shipmentRepository = shipmentRepository;
        this.trackingPort = trackingPort;
        this.finalizer = finalizer;
        this.clock = clock;
    }

    public void poll(UUID shipmentId) {
        Shipment shipment = shipmentRepository.findById(shipmentId)
                .orElseThrow(() -> new IllegalArgumentException("Shipment not found: " + shipmentId));
        try {
            ShipmentTrackingPort.TrackingResult result = trackingPort.track(
                    shipment.getCarrierCode(), shipment.getTrackingNumber());
            finalizer.apply(shipmentId, result);
        } catch (RuntimeException exception) {
            finalizer.recordFailure(shipmentId);
        }
    }

    public void markDeliveredBySystem(UUID orderId, Instant deliveredAt) {
        Shipment shipment = shipmentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Shipment not found for order: " + orderId));
        Instant effectiveDeliveredAt = deliveredAt == null ? clock.instant() : deliveredAt;
        if (effectiveDeliveredAt.isBefore(shipment.getCreatedAt()) || effectiveDeliveredAt.isAfter(clock.instant())) {
            throw new IllegalArgumentException("Delivered time must be between shipment creation and now");
        }
        finalizer.apply(shipment.getId(), new ShipmentTrackingPort.TrackingResult(
                shipment.getCarrierCode(),
                shipment.getTrackingNumber(),
                ShipmentStatus.DELIVERED,
                "manual-delivered:" + orderId,
                "Manually marked delivered by an administrator",
                effectiveDeliveredAt));
    }

    @Transactional(readOnly = true)
    public List<UUID> findDue(int limit) {
        return shipmentRepository.findTrackingDueIds(
                ShipmentStatus.DELIVERED, clock.instant(), PageRequest.of(0, bounded(limit)));
    }

    private static int bounded(int limit) {
        return Math.max(1, Math.min(limit, 500));
    }
}
