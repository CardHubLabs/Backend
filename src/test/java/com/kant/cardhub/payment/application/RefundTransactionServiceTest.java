package com.kant.cardhub.payment.application;

import com.kant.cardhub.order.application.port.ListingReservationPort;
import com.kant.cardhub.order.domain.Order;
import com.kant.cardhub.order.domain.OrderItemDraft;
import com.kant.cardhub.order.domain.OrderStatus;
import com.kant.cardhub.order.infrastructure.OrderRepository;
import com.kant.cardhub.payment.application.port.PaymentGateway;
import com.kant.cardhub.payment.application.port.RefundTradePort;
import com.kant.cardhub.payment.domain.Payment;
import com.kant.cardhub.payment.domain.Refund;
import com.kant.cardhub.payment.domain.RefundStatus;
import com.kant.cardhub.payment.infrastructure.PaymentRepository;
import com.kant.cardhub.payment.infrastructure.RefundRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RefundTransactionServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-25T00:00:00Z");

    @Test
    void approvedRefundReactivatesListingsAndExcludesTrades() {
        Fixture fixture = fixture(true);

        RefundStatus status = fixture.service.finalizeRefund(
                fixture.refund.getId(), PaymentGateway.PaymentGatewayResult.approved("CANCELLED", NOW));

        assertThat(status).isEqualTo(RefundStatus.APPROVED);
        assertThat(fixture.order.getStatus()).isEqualTo(OrderStatus.REFUNDED);
        verify(fixture.listings).reactivateSold(
                List.of(fixture.order.getItems().getFirst().getListingId()));
        verify(fixture.tradePort).excludeOrder(fixture.order.getId(), "REFUNDED", NOW);
    }

    @Test
    void declinedRefundReturnsOrderToPaidWithoutChangingListingsOrTrades() {
        Fixture fixture = fixture(true);

        RefundStatus status = fixture.service.finalizeRefund(
                fixture.refund.getId(), PaymentGateway.PaymentGatewayResult.declined("NOT_CANCELLABLE"));

        assertThat(status).isEqualTo(RefundStatus.FAILED);
        assertThat(fixture.order.getStatus()).isEqualTo(OrderStatus.PAID);
        verifyNoInteractions(fixture.listings, fixture.tradePort);
    }

    @Test
    void unknownRefundKeepsOrderRequestedAndDoesNotTouchCommerceState() {
        Fixture fixture = fixture(false);

        RefundStatus status = fixture.service.finalizeRefund(
                fixture.refund.getId(), PaymentGateway.PaymentGatewayResult.unknown("TIMEOUT"));

        assertThat(status).isEqualTo(RefundStatus.UNKNOWN);
        assertThat(fixture.order.getStatus()).isEqualTo(OrderStatus.REFUND_REQUESTED);
        verify(fixture.orderRepository, never()).findByIdForUpdate(any());
        verifyNoInteractions(fixture.listings, fixture.tradePort);
    }

    @SuppressWarnings("unchecked")
    private Fixture fixture(boolean exposeTradePort) {
        RefundRepository refundRepository = mock(RefundRepository.class);
        PaymentRepository paymentRepository = mock(PaymentRepository.class);
        OrderRepository orderRepository = mock(OrderRepository.class);
        ListingReservationPort listings = mock(ListingReservationPort.class);
        RefundTradePort tradePort = mock(RefundTradePort.class);
        ObjectProvider<RefundTradePort> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(exposeTradePort ? tradePort : null);

        Order order = refundableOrder();
        Payment payment = Payment.prepare(
                order.getId(), order.getBuyerId(), "idem", "payment-key", "toss-order",
                order.getTotalAmount(), NOW.minusSeconds(4));
        payment.approve("DONE", NOW.minusSeconds(3), NOW.minusSeconds(3));
        Refund refund = Refund.request(
                order.getId(), payment.getId(), order.getBuyerId(), payment.getAmount(), "changed mind",
                NOW.minusSeconds(1));
        refund.start(NOW.minusSeconds(1));
        when(refundRepository.findByIdForUpdate(refund.getId())).thenReturn(Optional.of(refund));
        when(orderRepository.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));

        RefundTransactionService service = new RefundTransactionService(
                refundRepository, paymentRepository, orderRepository, listings, provider,
                Clock.fixed(NOW, ZoneOffset.UTC));
        return new Fixture(service, orderRepository, listings, tradePort, order, refund);
    }

    private Order refundableOrder() {
        Order order = Order.create(
                UUID.randomUUID(), UUID.randomUUID(),
                List.of(new OrderItemDraft(UUID.randomUUID(), UUID.randomUUID(), "Card", 10_000, 3_000)),
                "encrypted", "v1", NOW.minusSeconds(60));
        order.beginPayment(order.getBuyerId(), order.getTotalAmount(), NOW.minusSeconds(5));
        order.markPaid(NOW.minusSeconds(4));
        order.requestRefund(order.getBuyerId(), NOW.minusSeconds(2));
        return order;
    }

    private record Fixture(
            RefundTransactionService service,
            OrderRepository orderRepository,
            ListingReservationPort listings,
            RefundTradePort tradePort,
            Order order,
            Refund refund
    ) {
    }
}
