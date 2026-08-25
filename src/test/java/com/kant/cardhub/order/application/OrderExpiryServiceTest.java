package com.kant.cardhub.order.application;

import com.kant.cardhub.order.application.port.ListingReservationPort;
import com.kant.cardhub.order.domain.Order;
import com.kant.cardhub.order.domain.OrderItemDraft;
import com.kant.cardhub.order.domain.OrderStatus;
import com.kant.cardhub.order.infrastructure.OrderRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class OrderExpiryServiceTest {

    private static final Instant CREATED_AT = Instant.parse("2026-08-25T00:00:00Z");
    private static final Instant EXPIRED_AT = CREATED_AT.plus(Order.RESERVATION_DURATION);

    @Test
    void paymentInFlightPreventsExpiryAndReservationRelease() {
        OrderRepository orderRepository = mock(OrderRepository.class);
        ListingReservationPort listings = mock(ListingReservationPort.class);
        Order order = order();
        order.beginPayment(order.getBuyerId(), order.getTotalAmount(), CREATED_AT.plusSeconds(1));
        when(orderRepository.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));

        boolean expired = service(orderRepository, listings).expireOne(order.getId());

        assertThat(expired).isFalse();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAYMENT_PENDING);
        assertThat(order.isPaymentInFlight()).isTrue();
        verifyNoInteractions(listings);
    }

    @Test
    void boundaryExpiryCancelsAndReleasesOnlyOwnedReservation() {
        OrderRepository orderRepository = mock(OrderRepository.class);
        ListingReservationPort listings = mock(ListingReservationPort.class);
        Order order = order();
        when(orderRepository.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));

        boolean expired = service(orderRepository, listings).expireOne(order.getId());

        assertThat(expired).isTrue();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        verify(listings).releaseOwned(
                List.of(order.getItems().getFirst().getListingId()), order.getId());
    }

    private OrderExpiryService service(OrderRepository orderRepository, ListingReservationPort listings) {
        return new OrderExpiryService(
                orderRepository, listings, Clock.fixed(EXPIRED_AT, ZoneOffset.UTC));
    }

    private Order order() {
        return Order.create(
                UUID.randomUUID(), UUID.randomUUID(),
                List.of(new OrderItemDraft(UUID.randomUUID(), UUID.randomUUID(), "Card", 10_000, 3_000)),
                "encrypted", "v1", CREATED_AT);
    }
}
