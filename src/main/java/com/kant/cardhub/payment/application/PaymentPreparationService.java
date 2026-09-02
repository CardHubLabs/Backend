package com.kant.cardhub.payment.application;

import com.kant.cardhub.common.error.BusinessException;
import com.kant.cardhub.common.error.ErrorCode;
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
        Payment sameRequest = paymentRepository
                .findByIdempotencyKeyForUpdate(command.idempotencyKey())
                .or(() -> paymentRepository.findByIdempotencyKey(command.idempotencyKey()))
                .orElse(null);
        if (sameRequest != null) {
            if (!sameRequest.requestMatches(
                    command.orderId(), command.buyerId(), command.paymentKey(), command.tossOrderId(), command.amount())) {
                throw new BusinessException(
                        ErrorCode.CONFLICT,
                        "An idempotency key cannot be reused with different payment data");
            }
            if (sameRequest.getStatus() == PaymentStatus.UNKNOWN && !sameRequest.isManualReviewRequired()) {
                sameRequest.beginConfirmRetry(clock.instant());
                return new Preparation(sameRequest.getId(), sameRequest.getStatus(), true);
            }
            return new Preparation(sameRequest.getId(), sameRequest.getStatus(), false);
        }
        if (paymentRepository.findByOrderId(command.orderId()).isPresent()) {
            throw new BusinessException(ErrorCode.PAYMENT_ALREADY_PROCESSED);
        }

        Order order = orderRepository.findByIdForUpdate(command.orderId())
                .orElseThrow(() -> new IllegalArgumentException("Order not found: " + command.orderId()));
        if (!order.getOrderNo().equals(command.tossOrderId())) {
            throw new IllegalArgumentException("tossOrderId must match the server-issued orderNo");
        }
        order.beginPayment(command.buyerId(), command.amount(), clock.instant());
        Payment payment = Payment.prepare(
                order.getId(), command.buyerId(), command.idempotencyKey(), command.paymentKey(),
                command.tossOrderId(), command.amount(), clock.instant());
        paymentRepository.save(payment);
        return new Preparation(payment.getId(), PaymentStatus.CONFIRMING, true);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public RetryAttempt prepareReconcile(java.util.UUID paymentId) {
        Payment payment = paymentRepository.findByIdForUpdate(paymentId)
                .orElseThrow(() -> new IllegalArgumentException("Payment not found: " + paymentId));
        if (payment.getStatus() != PaymentStatus.UNKNOWN
                && payment.getStatus() != PaymentStatus.CONFIRMING) {
            return new RetryAttempt(payment, false);
        }
        if (payment.isManualReviewRequired()) {
            return new RetryAttempt(payment, false);
        }
        payment.beginReconcileAttempt(clock.instant());
        return new RetryAttempt(payment, true);
    }

    public record Preparation(java.util.UUID paymentId, PaymentStatus status, boolean callGateway) {
    }

    public record RetryAttempt(Payment payment, boolean callGateway) {
    }
}
