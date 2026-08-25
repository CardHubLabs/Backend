package com.kant.cardhub.shipment.application;

import com.kant.cardhub.order.application.CommerceEventPublisher;
import com.kant.cardhub.order.application.port.CommerceEvent;
import com.kant.cardhub.order.application.port.CommerceEventType;
import com.kant.cardhub.order.domain.Order;
import com.kant.cardhub.order.infrastructure.OrderRepository;
import com.kant.cardhub.shipment.domain.Shipment;
import com.kant.cardhub.shipment.domain.ShipmentEvent;
import com.kant.cardhub.shipment.domain.ShipmentStatus;
import com.kant.cardhub.shipment.infrastructure.ShipmentEventRepository;
import com.kant.cardhub.shipment.infrastructure.ShipmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class ShipmentApplicationService {

    private final OrderRepository orderRepository;
    private final ShipmentRepository shipmentRepository;
    private final ShipmentEventRepository eventRepository;
    private final CommerceEventPublisher eventPublisher;
    private final Clock clock;

    public ShipmentApplicationService(
            OrderRepository orderRepository,
            ShipmentRepository shipmentRepository,
            ShipmentEventRepository eventRepository,
            CommerceEventPublisher eventPublisher,
            Clock clock
    ) {
        this.orderRepository = orderRepository;
        this.shipmentRepository = shipmentRepository;
        this.eventRepository = eventRepository;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Transactional
    public Shipment register(UUID sellerId, UUID orderId, String carrierCode, String trackingNumber) {
        if (shipmentRepository.findByOrderId(orderId).isPresent()) {
            throw new IllegalStateException("Shipment is already registered");
        }
        Order order = lockOrder(orderId);
        Instant now = clock.instant();
        order.markShipped(sellerId, now);
        Shipment shipment = shipmentRepository.save(
                Shipment.register(orderId, sellerId, carrierCode, trackingNumber, now));
        eventRepository.save(ShipmentEvent.create(
                shipment.getId(), "registered:" + shipment.getId(), ShipmentStatus.READY,
                "Shipment registered", now, now));
        eventPublisher.publish(CommerceEvent.orderEvent(
                CommerceEventType.SHIPPED, order.getBuyerId(), order.getId(), now));
        return shipment;
    }

    @Transactional
    public Shipment update(UUID sellerId, UUID orderId, String carrierCode, String trackingNumber) {
        lockOrder(orderId);
        Shipment shipment = shipmentRepository.findByOrderIdForUpdate(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Shipment not found for order: " + orderId));
        shipment.updateTracking(sellerId, carrierCode, trackingNumber, clock.instant());
        return shipment;
    }

    @Transactional(readOnly = true)
    public Shipment getForParticipant(UUID actorId, UUID orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found: " + orderId));
        if (!actorId.equals(order.getBuyerId()) && !actorId.equals(order.getSellerId())) {
            throw new SecurityException("Only order participants may view shipment details");
        }
        return shipmentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Shipment not found for order: " + orderId));
    }

    @Transactional(readOnly = true)
    public List<ShipmentEvent> timeline(UUID actorId, UUID orderId) {
        Shipment shipment = getForParticipant(actorId, orderId);
        return eventRepository.findByShipmentIdOrderByEventAtDesc(shipment.getId());
    }

    private Order lockOrder(UUID orderId) {
        return orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found: " + orderId));
    }
}
