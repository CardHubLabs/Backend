package com.kant.cardhub.shipment.application;

import com.kant.cardhub.order.application.CommerceEventPublisher;
import com.kant.cardhub.order.application.OrderConfirmationService;
import com.kant.cardhub.order.application.port.CommerceEvent;
import com.kant.cardhub.order.application.port.CommerceEventType;
import com.kant.cardhub.order.domain.Order;
import com.kant.cardhub.order.domain.OrderStatus;
import com.kant.cardhub.order.infrastructure.OrderRepository;
import com.kant.cardhub.shipment.domain.Shipment;
import com.kant.cardhub.shipment.domain.ShipmentStatus;
import com.kant.cardhub.shipment.infrastructure.ShipmentRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class ShipmentAutoConfirmationService {

    private final ShipmentRepository shipmentRepository;
    private final OrderRepository orderRepository;
    private final OrderConfirmationService confirmationService;
    private final CommerceEventPublisher eventPublisher;
    private final Clock clock;

    public ShipmentAutoConfirmationService(
            ShipmentRepository shipmentRepository,
            OrderRepository orderRepository,
            OrderConfirmationService confirmationService,
            CommerceEventPublisher eventPublisher,
            Clock clock
    ) {
        this.shipmentRepository = shipmentRepository;
        this.orderRepository = orderRepository;
        this.confirmationService = confirmationService;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<UUID> findNoticeDue(int limit) {
        return shipmentRepository.findNoticeDueIds(
                ShipmentStatus.DELIVERED,
                OrderStatus.DELIVERED,
                clock.instant().minus(Shipment.AUTO_CONFIRM_NOTICE_DELAY),
                PageRequest.of(0, bounded(limit)));
    }

    @Transactional(readOnly = true)
    public List<UUID> findAutoConfirmDue(int limit) {
        return shipmentRepository.findAutoConfirmDueIds(
                ShipmentStatus.DELIVERED,
                OrderStatus.DELIVERED,
                clock.instant().minus(Shipment.AUTO_CONFIRM_DELAY),
                PageRequest.of(0, bounded(limit)));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean sendNotice(UUID shipmentId) {
        Shipment snapshot = shipmentRepository.findById(shipmentId).orElse(null);
        if (snapshot == null) {
            return false;
        }
        Order order = orderRepository.findByIdForUpdate(snapshot.getOrderId()).orElse(null);
        Shipment shipment = shipmentRepository.findByIdForUpdate(shipmentId).orElse(null);
        Instant now = clock.instant();
        if (order == null || order.getStatus() != OrderStatus.DELIVERED
                || shipment == null || !shipment.noticeDue(now)) {
            return false;
        }
        shipment.markNoticeSent(now);
        eventPublisher.publish(CommerceEvent.orderEvent(
                CommerceEventType.AUTO_CONFIRM_NOTICE, order.getBuyerId(), order.getId(), now));
        return true;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<UUID> autoConfirm(UUID shipmentId) {
        Shipment snapshot = shipmentRepository.findById(shipmentId)
                .orElseThrow(() -> new IllegalArgumentException("Shipment not found: " + shipmentId));
        Order order = orderRepository.findByIdForUpdate(snapshot.getOrderId()).orElseThrow();
        Shipment shipment = shipmentRepository.findByIdForUpdate(shipmentId).orElseThrow();
        if (order.getStatus() != OrderStatus.DELIVERED || !shipment.autoConfirmDue(clock.instant())) {
            return List.of();
        }
        return confirmationService.autoConfirm(shipment.getOrderId());
    }

    @Transactional
    public void hold(UUID buyerId, UUID orderId) {
        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found: " + orderId));
        Shipment shipment = shipmentRepository.findByOrderIdForUpdate(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Shipment not found for order: " + orderId));
        if (order.getStatus() != OrderStatus.DELIVERED) {
            throw new IllegalStateException("Only a delivered order can hold auto confirmation");
        }
        shipment.hold(buyerId, order.getBuyerId(), clock.instant());
    }

    @Transactional
    public void releaseHold(UUID orderId) {
        orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found: " + orderId));
        Shipment shipment = shipmentRepository.findByOrderIdForUpdate(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Shipment not found for order: " + orderId));
        shipment.releaseHold(clock.instant());
    }

    private static int bounded(int limit) {
        return Math.max(1, Math.min(limit, 500));
    }
}
