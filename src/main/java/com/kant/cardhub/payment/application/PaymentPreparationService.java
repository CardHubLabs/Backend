package com.kant.cardhub.payment.application;

import com.kant.cardhub.order.domain.Order;
import com.kant.cardhub.order.infrastructure.OrderRepository;
import com.kant.cardhub.payment.domain.Payment;
import com.kant.cardhub.payment.domain.PaymentStatus;
import com.kant.cardhub.payment.infrastructure.PaymentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

@Service
public class PaymentPreparationService {

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final Clock clock;

    public PaymentPreparationService(PaymentRepository paymentRepository, OrderRepository orderRepository, Clock clock) {
        this.paymentRepository = paymentRepository;
        this.orderRepository = orderRepository;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Preparation prepare(PaymentCommand command) {
        Payment sameRequest = paymentRepository.findByIdempotencyKey(command.idempotencyKey()).orElse(null);
        if (sameRequest != null) {
            if (!sameRequest.requestMatches(
                    command.orderId(), command.buyerId(), command.paymentKey(), command.tossOrderId(), command.amount())) {
                throw new IllegalArgumentException("An idempotency key cannot be reused with different payment data");
            }
            return new Preparation(sameRequest.getId(), sameRequest.getStatus(), false);
        }
        if (paymentRepository.findByOrderId(command.orderId()).isPresent()) {
            throw new IllegalStateException("This order already has a payment attempt");
        }

        Order order = orderRepository.findByIdForUpdate(command.orderId())
                .orElseThrow(() -> new IllegalArgumentException("Order not found: " + command.orderId()));
        order.beginPayment(command.buyerId(), command.amount(), clock.instant());
        Payment payment = Payment.prepare(
                order.getId(), command.buyerId(), command.idempotencyKey(), command.paymentKey(),
                command.tossOrderId(), command.amount(), clock.instant());
        paymentRepository.save(payment);
        return new Preparation(payment.getId(), PaymentStatus.CONFIRMING, true);
    }

    public record Preparation(java.util.UUID paymentId, PaymentStatus status, boolean callGateway) {
    }
}
