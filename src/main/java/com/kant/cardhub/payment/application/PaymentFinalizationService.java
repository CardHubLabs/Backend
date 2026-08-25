package com.kant.cardhub.payment.application;

import com.kant.cardhub.order.application.CommerceEventPublisher;
import com.kant.cardhub.order.application.OrderApplicationService;
import com.kant.cardhub.order.application.port.CommerceEvent;
import com.kant.cardhub.order.application.port.CommerceEventType;
import com.kant.cardhub.order.application.port.ListingReservationPort;
import com.kant.cardhub.order.domain.Order;
import com.kant.cardhub.order.infrastructure.OrderRepository;
import com.kant.cardhub.payment.application.port.PaymentGateway;
import com.kant.cardhub.payment.domain.Payment;
import com.kant.cardhub.payment.infrastructure.PaymentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

@Service
public class PaymentFinalizationService {

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final ListingReservationPort listingReservationPort;
    private final CommerceEventPublisher eventPublisher;
    private final Clock clock;

    public PaymentFinalizationService(
            PaymentRepository paymentRepository,
            OrderRepository orderRepository,
            ListingReservationPort listingReservationPort,
            CommerceEventPublisher eventPublisher,
            Clock clock
    ) {
        this.paymentRepository = paymentRepository;
        this.orderRepository = orderRepository;
        this.listingReservationPort = listingReservationPort;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PaymentResult finalizeResult(java.util.UUID paymentId, PaymentGateway.PaymentGatewayResult gatewayResult) {
        Payment payment = paymentRepository.findByIdForUpdate(paymentId)
                .orElseThrow(() -> new IllegalArgumentException("Payment not found: " + paymentId));
        if (payment.getStatus() == com.kant.cardhub.payment.domain.PaymentStatus.APPROVED
                || payment.getStatus() == com.kant.cardhub.payment.domain.PaymentStatus.FAILED) {
            return toResult(payment);
        }
        Order order = orderRepository.findByIdForUpdate(payment.getOrderId())
                .orElseThrow(() -> new IllegalStateException("Payment order no longer exists"));
        Instant now = clock.instant();
        switch (gatewayResult.status()) {
            case APPROVED -> {
                listingReservationPort.markSold(OrderApplicationService.listingIds(order), order.getId());
                payment.approve(gatewayResult.providerCode(), gatewayResult.approvedAt(), now);
                order.markPaid(now);
                eventPublisher.publish(CommerceEvent.orderEvent(
                        CommerceEventType.PAYMENT_DONE, order.getBuyerId(), order.getId(), now));
                eventPublisher.publish(CommerceEvent.orderEvent(
                        CommerceEventType.SALE_MADE, order.getSellerId(), order.getId(), now));
            }
            case DECLINED -> {
                payment.fail(gatewayResult.providerCode(), now);
                order.cancelAfterDefinitivePaymentFailure(now);
                listingReservationPort.releaseOwned(OrderApplicationService.listingIds(order), order.getId());
            }
            case UNKNOWN -> payment.unknown(gatewayResult.providerCode(), now);
        }
        return toResult(payment);
    }

    private static PaymentResult toResult(Payment payment) {
        return new PaymentResult(payment.getId(), payment.getOrderId(), payment.getStatus());
    }
}
