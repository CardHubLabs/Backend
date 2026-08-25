package com.kant.cardhub.payment.application;

import com.kant.cardhub.order.application.OrderApplicationService;
import com.kant.cardhub.order.application.port.ListingReservationPort;
import com.kant.cardhub.order.domain.Order;
import com.kant.cardhub.order.infrastructure.OrderRepository;
import com.kant.cardhub.payment.application.port.PaymentGateway;
import com.kant.cardhub.payment.application.port.RefundTradePort;
import com.kant.cardhub.payment.domain.Payment;
import com.kant.cardhub.payment.domain.Refund;
import com.kant.cardhub.payment.domain.RefundStatus;
import com.kant.cardhub.payment.infrastructure.PaymentRepository;
import com.kant.cardhub.payment.infrastructure.RefundRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import java.time.Duration;

@Service
public class RefundTransactionService {

    private final RefundRepository refundRepository;
    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final ListingReservationPort listingReservationPort;
    private final ObjectProvider<RefundTradePort> refundTradePort;
    private final Clock clock;

    public RefundTransactionService(
            RefundRepository refundRepository,
            PaymentRepository paymentRepository,
            OrderRepository orderRepository,
            ListingReservationPort listingReservationPort,
            ObjectProvider<RefundTradePort> refundTradePort,
            Clock clock
    ) {
        this.refundRepository = refundRepository;
        this.paymentRepository = paymentRepository;
        this.orderRepository = orderRepository;
        this.listingReservationPort = listingReservationPort;
        this.refundTradePort = refundTradePort;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PreparedRefund prepare(UUID refundId) {
        Refund refund = refundRepository.findByIdForUpdate(refundId)
                .orElseThrow(() -> new IllegalArgumentException("Refund not found: " + refundId));
        refund.start(clock.instant());
        Payment payment = paymentRepository.findById(refund.getPaymentId()).orElseThrow();
        return new PreparedRefund(payment.getPaymentKey(), refund.getAmount(), refund.getReason());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public RefundStatus finalizeRefund(UUID refundId, PaymentGateway.PaymentGatewayResult result) {
        Refund refund = refundRepository.findByIdForUpdate(refundId)
                .orElseThrow(() -> new IllegalArgumentException("Refund not found: " + refundId));
        if (refund.getStatus() == RefundStatus.APPROVED || refund.getStatus() == RefundStatus.FAILED) {
            return refund.getStatus();
        }
        Instant now = clock.instant();
        switch (result.status()) {
            case APPROVED -> {
                Order order = orderRepository.findByIdForUpdate(refund.getOrderId()).orElseThrow();
                refund.approve(result.providerCode(), now);
                order.markRefunded(now);
                listingReservationPort.reactivateSold(OrderApplicationService.listingIds(order));
                RefundTradePort tradePort = refundTradePort.getIfAvailable();
                if (tradePort != null) {
                    tradePort.excludeOrder(order.getId(), "REFUNDED", now);
                }
            }
            case DECLINED -> {
                Order order = orderRepository.findByIdForUpdate(refund.getOrderId()).orElseThrow();
                refund.fail(result.providerCode(), now);
                order.rejectRefund(now);
            }
            case UNKNOWN -> refund.unknown(result.providerCode(), now);
        }
        return refund.getStatus();
    }

    @Transactional(readOnly = true)
    public List<UUID> findRecoverable(int limit) {
        return refundRepository.findRecoverableIds(
                RefundStatus.UNKNOWN,
                RefundStatus.PROCESSING,
                clock.instant().minus(Duration.ofMinutes(2)),
                PageRequest.of(0, limit));
    }

    public record PreparedRefund(String paymentKey, long amount, String reason) {
    }
}
