package com.kant.cardhub.shipment.application;

import com.kant.cardhub.order.application.CommerceEventPublisher;
import com.kant.cardhub.order.application.OrderConfirmationService;
import com.kant.cardhub.order.domain.Order;
import com.kant.cardhub.order.domain.OrderItemDraft;
import com.kant.cardhub.order.domain.OrderStatus;
import com.kant.cardhub.order.infrastructure.OrderRepository;
import com.kant.cardhub.shipment.domain.Shipment;
import com.kant.cardhub.shipment.domain.ShipmentStatus;
import com.kant.cardhub.shipment.infrastructure.ShipmentRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ShipmentAutoConfirmationServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-25T00:00:00Z");
    private static final Instant DELIVERED_AT = NOW.minus(Shipment.AUTO_CONFIRM_DELAY);

    @Test
    void holdAppliedAfterDueScanPreventsAutoConfirmation() {
        Fixture fixture = fixture(false);
        fixture.shipment.hold(fixture.order.getBuyerId(), fixture.order.getBuyerId(), NOW.minusSeconds(1));

        List<UUID> trades = fixture.service.autoConfirm(fixture.shipment.getId());

        assertThat(trades).isEmpty();
        assertThat(fixture.order.getStatus()).isEqualTo(OrderStatus.DELIVERED);
        verify(fixture.confirmationService, never()).autoConfirm(fixture.order.getId());
    }

    @Test
    void orderConfirmedAfterDueScanCannotBeConfirmedTwice() {
        Fixture fixture = fixture(true);

        List<UUID> trades = fixture.service.autoConfirm(fixture.shipment.getId());

        assertThat(trades).isEmpty();
        assertThat(fixture.order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        verify(fixture.confirmationService, never()).autoConfirm(fixture.order.getId());
    }

    @Test
    void dueAndUnlockedShipmentDelegatesToAtomicOrderConfirmation() {
        Fixture fixture = fixture(false);
        List<UUID> expectedTrades = List.of(UUID.randomUUID(), UUID.randomUUID());
        when(fixture.confirmationService.autoConfirm(fixture.order.getId())).thenReturn(expectedTrades);

        List<UUID> trades = fixture.service.autoConfirm(fixture.shipment.getId());

        assertThat(trades).isEqualTo(expectedTrades);
        verify(fixture.confirmationService).autoConfirm(fixture.order.getId());
    }

    private Fixture fixture(boolean alreadyConfirmed) {
        ShipmentRepository shipmentRepository = mock(ShipmentRepository.class);
        OrderRepository orderRepository = mock(OrderRepository.class);
        OrderConfirmationService confirmationService = mock(OrderConfirmationService.class);
        CommerceEventPublisher eventPublisher = mock(CommerceEventPublisher.class);
        UUID sellerId = UUID.randomUUID();
        Order order = deliveredOrder(sellerId);
        Shipment shipment = Shipment.register(order.getId(), sellerId, "kr.cjlogistics", "123456", DELIVERED_AT.minusSeconds(1));
        shipment.applyTrackingStatus(ShipmentStatus.DELIVERED, DELIVERED_AT, DELIVERED_AT);
        if (alreadyConfirmed) {
            order.confirm(order.getBuyerId(), DELIVERED_AT.plusSeconds(1));
        }
        when(shipmentRepository.findById(shipment.getId())).thenReturn(Optional.of(shipment));
        when(shipmentRepository.findByIdForUpdate(shipment.getId())).thenReturn(Optional.of(shipment));
        when(orderRepository.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));
        ShipmentAutoConfirmationService service = new ShipmentAutoConfirmationService(
                shipmentRepository, orderRepository, confirmationService, eventPublisher,
                Clock.fixed(NOW, ZoneOffset.UTC));
        return new Fixture(service, confirmationService, order, shipment);
    }

    private Order deliveredOrder(UUID sellerId) {
        Instant createdAt = DELIVERED_AT.minusSeconds(10);
        Order order = Order.create(
                UUID.randomUUID(), sellerId,
                List.of(new OrderItemDraft(UUID.randomUUID(), UUID.randomUUID(), "Card", 10_000, 3_000)),
                "encrypted", "v1", createdAt);
        order.beginPayment(order.getBuyerId(), order.getTotalAmount(), createdAt.plusSeconds(1));
        order.markPaid(createdAt.plusSeconds(2));
        order.markShipped(sellerId, createdAt.plusSeconds(3));
        order.markDelivered(DELIVERED_AT);
        return order;
    }

    private record Fixture(
            ShipmentAutoConfirmationService service,
            OrderConfirmationService confirmationService,
            Order order,
            Shipment shipment
    ) {
    }
}
