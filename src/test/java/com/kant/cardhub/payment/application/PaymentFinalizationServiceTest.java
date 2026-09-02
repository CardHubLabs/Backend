package com.kant.cardhub.payment.application;

import com.kant.cardhub.order.application.CommerceEventPublisher;
import com.kant.cardhub.order.application.port.ListingReservationPort;
import com.kant.cardhub.order.domain.Order;
import com.kant.cardhub.order.domain.OrderItemDraft;
import com.kant.cardhub.order.domain.OrderStatus;
import com.kant.cardhub.order.infrastructure.OrderRepository;
import com.kant.cardhub.payment.application.port.PaymentGateway;
import com.kant.cardhub.payment.domain.Payment;
import com.kant.cardhub.payment.domain.PaymentStatus;
import com.kant.cardhub.payment.infrastructure.PaymentRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PaymentFinalizationServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-25T00:00:00Z");

    @Test
    void approvedPaymentSellsListingsPaysOrderAndPublishesBothNotifications() {
        Fixture fixture = fixture();

        PaymentResult result = fixture.service.finalizeResult(
                fixture.payment.getId(), PaymentGateway.PaymentGatewayResult.approved("DONE", NOW.minusSeconds(1)));

        assertThat(result.status()).isEqualTo(PaymentStatus.APPROVED);
        assertThat(fixture.payment.getStatus()).isEqualTo(PaymentStatus.APPROVED);
        assertThat(fixture.order.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(fixture.order.isPaymentInFlight()).isFalse();
        verify(fixture.listings).markSold(
                List.of(fixture.order.getItems().getFirst().getListingId()), fixture.order.getId());
        verify(fixture.events, times(2)).publish(any());
    }

    @Test
    void declinedPaymentCancelsOrderAndReleasesOnlyItsReservation() {
        Fixture fixture = fixture();

        PaymentResult result = fixture.service.finalizeResult(
                fixture.payment.getId(), PaymentGateway.PaymentGatewayResult.declined("DECLINED"));

        assertThat(result.status()).isEqualTo(PaymentStatus.FAILED);
        assertThat(fixture.order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(fixture.order.isPaymentInFlight()).isFalse();
        verify(fixture.listings).releaseOwned(
                List.of(fixture.order.getItems().getFirst().getListingId()), fixture.order.getId());
        verify(fixture.listings, never()).markSold(any(), any());
        verifyNoInteractions(fixture.events);
    }

    @Test
    void unknownPaymentPreservesPendingOrderAndReservationForRecovery() {
        Fixture fixture = fixture();

        PaymentResult result = fixture.service.finalizeResult(
                fixture.payment.getId(), PaymentGateway.PaymentGatewayResult.unknown("TIMEOUT"));

        assertThat(result.status()).isEqualTo(PaymentStatus.UNKNOWN);
        assertThat(fixture.order.getStatus()).isEqualTo(OrderStatus.PAYMENT_PENDING);
        assertThat(fixture.order.isPaymentInFlight()).isTrue();
        verifyNoInteractions(fixture.listings, fixture.events);
    }

    @Test
    void providerIdentityMismatchStaysUnknownAndRequiresManualReview() {
        Fixture fixture = fixture();

        PaymentResult result = fixture.service.finalizeResult(
                fixture.payment.getId(),
                PaymentGateway.PaymentGatewayResult.providerApproved(
                        "DONE",
                        NOW,
                        fixture.payment.getPaymentKey(),
                        "different-order-id",
                        fixture.payment.getAmount(),
                        "CARD",
                        null,
                        "DONE"
                ));

        assertThat(result.status()).isEqualTo(PaymentStatus.UNKNOWN);
        assertThat(fixture.payment.isManualReviewRequired()).isTrue();
        assertThat(fixture.order.getStatus()).isEqualTo(OrderStatus.PAYMENT_PENDING);
        assertThat(fixture.order.isPaymentInFlight()).isTrue();
        verifyNoInteractions(fixture.listings, fixture.events);
    }

    @Test
    void definitiveDuplicateReturnsExistingPaymentWithoutTouchingOrderAgain() {
        Fixture fixture = fixture();
        fixture.payment.approve("DONE", NOW.minusSeconds(1), NOW.minusSeconds(1));
        fixture.order.markPaid(NOW.minusSeconds(1));

        PaymentResult result = fixture.service.finalizeResult(
                fixture.payment.getId(), PaymentGateway.PaymentGatewayResult.declined("LATE_DECLINE"));

        assertThat(result.status()).isEqualTo(PaymentStatus.APPROVED);
        assertThat(fixture.order.getStatus()).isEqualTo(OrderStatus.PAID);
        verify(fixture.orderRepository, never()).findByIdForUpdate(any());
        verifyNoInteractions(fixture.listings, fixture.events);
    }

    private Fixture fixture() {
        PaymentRepository paymentRepository = mock(PaymentRepository.class);
        OrderRepository orderRepository = mock(OrderRepository.class);
        ListingReservationPort listings = mock(ListingReservationPort.class);
        CommerceEventPublisher events = mock(CommerceEventPublisher.class);
        Order order = pendingPaymentOrder();
        Payment payment = Payment.prepare(
                order.getId(), order.getBuyerId(), "idem", "payment-key", "toss-order",
                order.getTotalAmount(), NOW.minusSeconds(2));
        when(paymentRepository.findByIdForUpdate(payment.getId())).thenReturn(Optional.of(payment));
        when(orderRepository.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));
        PaymentFinalizationService service = new PaymentFinalizationService(
                paymentRepository, orderRepository, listings, events, Clock.fixed(NOW, ZoneOffset.UTC));
        return new Fixture(service, paymentRepository, orderRepository, listings, events, order, payment);
    }

    private Order pendingPaymentOrder() {
        Order order = Order.create(
                UUID.randomUUID(), UUID.randomUUID(),
                List.of(new OrderItemDraft(UUID.randomUUID(), UUID.randomUUID(), "Card", 10_000, 3_000)),
                "encrypted", "v1", NOW.minusSeconds(60));
        order.beginPayment(order.getBuyerId(), order.getTotalAmount(), NOW.minusSeconds(2));
        return order;
    }

    private record Fixture(
            PaymentFinalizationService service,
            PaymentRepository paymentRepository,
            OrderRepository orderRepository,
            ListingReservationPort listings,
            CommerceEventPublisher events,
            Order order,
            Payment payment
    ) {
    }
}
