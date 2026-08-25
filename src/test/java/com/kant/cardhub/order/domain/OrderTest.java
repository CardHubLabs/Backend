package com.kant.cardhub.order.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderTest {

    private static final Instant NOW = Instant.parse("2026-08-25T00:00:00Z");
    private final UUID buyerId = UUID.randomUUID();
    private final UUID sellerId = UUID.randomUUID();

    @Test
    void groupedOrderUsesOneHighestShippingFee() {
        Order order = order();

        assertThat(order.getItemSubtotal()).isEqualTo(3_000);
        assertThat(order.getShippingFee()).isEqualTo(700);
        assertThat(order.getTotalAmount()).isEqualTo(3_700);
        assertThat(order.getItems()).hasSize(2);
        assertThat(order.getReservationExpiresAt()).isEqualTo(NOW.plus(Order.RESERVATION_DURATION));
    }

    @Test
    void reservationBoundaryIsExpiredAndCannotBeginPayment() {
        Order order = order();

        assertThatThrownBy(() -> order.beginPayment(
                buyerId, order.getTotalAmount(), order.getReservationExpiresAt()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("expired");
    }

    @Test
    void inFlightPaymentPreventsBuyerAndExpiryCancellation() {
        Order order = order();
        order.beginPayment(buyerId, order.getTotalAmount(), NOW.plusSeconds(1));

        assertThatThrownBy(() -> order.cancelByBuyer(buyerId, NOW.plusSeconds(2)))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> order.cancelExpired(order.getReservationExpiresAt()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void onlyBuyerCanConfirmDeliveredOrder() {
        Order order = order();
        order.beginPayment(buyerId, order.getTotalAmount(), NOW.plusSeconds(1));
        order.markPaid(NOW.plusSeconds(2));
        order.markShipped(sellerId, NOW.plusSeconds(3));
        order.markDelivered(NOW.plusSeconds(4));

        assertThatThrownBy(() -> order.confirm(UUID.randomUUID(), NOW.plusSeconds(5)))
                .isInstanceOf(SecurityException.class);
        order.confirm(buyerId, NOW.plusSeconds(5));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(order.getConfirmedAt()).isEqualTo(NOW.plusSeconds(5));
    }

    @Test
    void cannotOrderOwnListing() {
        assertThatThrownBy(() -> Order.create(
                sellerId, sellerId, drafts(), "encrypted", "v1", NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private Order order() {
        return Order.create(buyerId, sellerId, drafts(), "encrypted", "v1", NOW);
    }

    private List<OrderItemDraft> drafts() {
        return List.of(
                new OrderItemDraft(UUID.randomUUID(), UUID.randomUUID(), "A", 1_000, 700),
                new OrderItemDraft(UUID.randomUUID(), UUID.randomUUID(), "B", 2_000, 300)
        );
    }
}
