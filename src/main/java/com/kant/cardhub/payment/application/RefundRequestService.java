package com.kant.cardhub.payment.application;

import com.kant.cardhub.order.domain.Order;
import com.kant.cardhub.order.infrastructure.OrderRepository;
import com.kant.cardhub.payment.domain.Payment;
import com.kant.cardhub.payment.domain.PaymentStatus;
import com.kant.cardhub.payment.domain.Refund;
import com.kant.cardhub.payment.infrastructure.PaymentRepository;
import com.kant.cardhub.payment.infrastructure.RefundRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

@Service
public class RefundRequestService {

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final RefundRepository refundRepository;
    private final Clock clock;

    public RefundRequestService(
            OrderRepository orderRepository,
            PaymentRepository paymentRepository,
            RefundRepository refundRepository,
            Clock clock
    ) {
        this.orderRepository = orderRepository;
        this.paymentRepository = paymentRepository;
        this.refundRepository = refundRepository;
        this.clock = clock;
    }

    @Transactional
    public Refund request(UUID buyerId, UUID orderId, String reason) {
        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found: " + orderId));
        Payment payment = paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new IllegalStateException("Approved payment not found"));
        if (payment.getStatus() != PaymentStatus.APPROVED) {
            throw new IllegalStateException("Only an approved payment can be refunded");
        }
        order.requestRefund(buyerId, clock.instant());
        Refund existing = refundRepository.findByOrderIdForUpdate(orderId).orElse(null);
        if (existing != null) {
            if (existing.getStatus() != com.kant.cardhub.payment.domain.RefundStatus.FAILED) {
                throw new IllegalStateException("A refund already exists for this order");
            }
            existing.retry(reason, clock.instant());
            return existing;
        }
        return refundRepository.save(Refund.request(
                orderId, payment.getId(), buyerId, payment.getAmount(), reason, clock.instant()));
    }

    @Transactional(readOnly = true)
    public Refund findForOrder(UUID orderId) {
        return refundRepository.findByOrderId(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Refund not found for order: " + orderId));
    }
}
