package com.kant.cardhub.payment.application;

import com.kant.cardhub.order.domain.Order;
import com.kant.cardhub.order.domain.OrderItemDraft;
import com.kant.cardhub.order.infrastructure.OrderRepository;
import com.kant.cardhub.payment.domain.Payment;
import com.kant.cardhub.payment.domain.PaymentStatus;
import com.kant.cardhub.payment.infrastructure.PaymentRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PaymentPreparationServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-25T00:00:00Z");

    @Test
    void preparesOrderAndPaymentBeforeGatewayCall() {
        PaymentRepository paymentRepository = mock(PaymentRepository.class);
        OrderRepository orderRepository = mock(OrderRepository.class);
        Order order = order();
        PaymentCommand command = command(order, "idem-1", order.getTotalAmount());
        when(paymentRepository.findByIdempotencyKey(command.idempotencyKey())).thenReturn(Optional.empty());
        when(paymentRepository.findByOrderId(order.getId())).thenReturn(Optional.empty());
        when(orderRepository.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));

        PaymentPreparationService.Preparation result = service(paymentRepository, orderRepository).prepare(command);

        assertThat(result.status()).isEqualTo(PaymentStatus.CONFIRMING);
        assertThat(result.callGateway()).isTrue();
        assertThat(order.isPaymentInFlight()).isTrue();
        ArgumentCaptor<Payment> saved = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(saved.capture());
        assertThat(saved.getValue().requestMatches(
                command.orderId(), command.buyerId(), command.paymentKey(),
                command.tossOrderId(), command.amount())).isTrue();
    }

    @Test
    void exactDuplicateReturnsExistingResultWithoutCallingGatewayAgain() {
        PaymentRepository paymentRepository = mock(PaymentRepository.class);
        OrderRepository orderRepository = mock(OrderRepository.class);
        Order order = order();
        PaymentCommand command = command(order, "idem-duplicate", order.getTotalAmount());
        Payment existing = Payment.prepare(
                order.getId(), order.getBuyerId(), command.idempotencyKey(), command.paymentKey(),
                command.tossOrderId(), command.amount(), NOW.minusSeconds(1));
        when(paymentRepository.findByIdempotencyKey(command.idempotencyKey())).thenReturn(Optional.of(existing));

        PaymentPreparationService.Preparation result = service(paymentRepository, orderRepository).prepare(command);

        assertThat(result.paymentId()).isEqualTo(existing.getId());
        assertThat(result.status()).isEqualTo(PaymentStatus.CONFIRMING);
        assertThat(result.callGateway()).isFalse();
        verifyNoInteractions(orderRepository);
        verify(paymentRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void idempotencyKeyReuseWithDifferentRequestIsRejected() {
        PaymentRepository paymentRepository = mock(PaymentRepository.class);
        OrderRepository orderRepository = mock(OrderRepository.class);
        Order order = order();
        PaymentCommand original = command(order, "idem-conflict", order.getTotalAmount());
        Payment existing = Payment.prepare(
                order.getId(), order.getBuyerId(), original.idempotencyKey(), original.paymentKey(),
                original.tossOrderId(), original.amount(), NOW.minusSeconds(1));
        when(paymentRepository.findByIdempotencyKey(original.idempotencyKey())).thenReturn(Optional.of(existing));
        PaymentCommand changed = command(order, original.idempotencyKey(), original.amount() + 1);

        assertThatThrownBy(() -> service(paymentRepository, orderRepository).prepare(changed))
                .isInstanceOf(com.kant.cardhub.common.error.BusinessException.class)
                .hasMessageContaining("idempotency key");
        verifyNoInteractions(orderRepository);
    }

    @Test
    void exactUnknownDuplicateRetriesWithTheSameStoredPayload() {
        PaymentRepository paymentRepository = mock(PaymentRepository.class);
        OrderRepository orderRepository = mock(OrderRepository.class);
        Order order = order();
        PaymentCommand command = command(order, "idem-retry", order.getTotalAmount());
        Payment existing = Payment.prepare(
                order.getId(), order.getBuyerId(), command.idempotencyKey(), command.paymentKey(),
                command.tossOrderId(), command.amount(), NOW.minusSeconds(30));
        existing.unknown("TIMEOUT", NOW.minusSeconds(20));
        when(paymentRepository.findByIdempotencyKeyForUpdate(command.idempotencyKey()))
                .thenReturn(Optional.of(existing));

        PaymentPreparationService.Preparation result = service(paymentRepository, orderRepository).prepare(command);

        assertThat(result.callGateway()).isTrue();
        assertThat(result.status()).isEqualTo(PaymentStatus.CONFIRMING);
        assertThat(existing.getConfirmAttemptCount()).isEqualTo(2);
        assertThat(existing.getIdempotencyKey()).isEqualTo(command.idempotencyKey());
        verifyNoInteractions(orderRepository);
    }

    private PaymentPreparationService service(
            PaymentRepository paymentRepository,
            OrderRepository orderRepository
    ) {
        return new PaymentPreparationService(
                paymentRepository, orderRepository, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private PaymentCommand command(Order order, String idempotencyKey, long amount) {
        return new PaymentCommand(
                order.getBuyerId(), order.getId(), "payment-key", order.getOrderNo(), amount, idempotencyKey);
    }

    private Order order() {
        return Order.create(
                UUID.randomUUID(), UUID.randomUUID(),
                List.of(new OrderItemDraft(UUID.randomUUID(), UUID.randomUUID(), "Card", 10_000, 3_000)),
                "encrypted", "v1", NOW.minusSeconds(60));
    }
}
