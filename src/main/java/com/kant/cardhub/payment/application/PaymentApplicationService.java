package com.kant.cardhub.payment.application;

import com.kant.cardhub.payment.application.port.PaymentGateway;
import com.kant.cardhub.payment.domain.Payment;
import com.kant.cardhub.payment.domain.PaymentStatus;
import com.kant.cardhub.payment.infrastructure.PaymentRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.time.Clock;
import java.time.Duration;

@Service
public class PaymentApplicationService {

    private final PaymentPreparationService preparationService;
    private final PaymentFinalizationService finalizationService;
    private final PaymentRepository paymentRepository;
    private final PaymentGateway paymentGateway;
    private final Clock clock;

    public PaymentApplicationService(
            PaymentPreparationService preparationService,
            PaymentFinalizationService finalizationService,
            PaymentRepository paymentRepository,
            PaymentGateway paymentGateway,
            Clock clock
    ) {
        this.preparationService = preparationService;
        this.finalizationService = finalizationService;
        this.paymentRepository = paymentRepository;
        this.paymentGateway = paymentGateway;
        this.clock = clock;
    }

    public PaymentResult confirm(PaymentCommand command) {
        PaymentPreparationService.Preparation preparation = preparationService.prepare(command);
        if (!preparation.callGateway()) {
            Payment existing = paymentRepository.findById(preparation.paymentId()).orElseThrow();
            return new PaymentResult(existing.getId(), existing.getOrderId(), existing.getStatus());
        }
        PaymentGateway.PaymentGatewayResult gatewayResult;
        try {
            gatewayResult = paymentGateway.confirm(new PaymentGateway.ConfirmRequest(
                    command.paymentKey(), command.tossOrderId(), command.amount(), command.idempotencyKey()));
        } catch (RuntimeException exception) {
            gatewayResult = PaymentGateway.PaymentGatewayResult.unknown("GATEWAY_CALL_FAILED");
        }
        return finalizationService.finalizeResult(preparation.paymentId(), gatewayResult);
    }

    public PaymentResult reconcile(UUID paymentId) {
        Payment snapshot = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new IllegalArgumentException("Payment not found: " + paymentId));
        if (snapshot.getStatus() != PaymentStatus.UNKNOWN && snapshot.getStatus() != PaymentStatus.CONFIRMING) {
            return new PaymentResult(snapshot.getId(), snapshot.getOrderId(), snapshot.getStatus());
        }
        PaymentGateway.PaymentGatewayResult result;
        try {
            result = paymentGateway.lookup(snapshot.getPaymentKey());
        } catch (RuntimeException exception) {
            result = PaymentGateway.PaymentGatewayResult.unknown("GATEWAY_LOOKUP_FAILED");
        }
        return finalizationService.finalizeResult(paymentId, result);
    }

    @Transactional(readOnly = true)
    public List<UUID> findUnknown(int limit) {
        return paymentRepository.findRecoverableIds(
                PaymentStatus.UNKNOWN,
                PaymentStatus.CONFIRMING,
                clock.instant().minus(Duration.ofMinutes(2)),
                PageRequest.of(0, Math.max(1, Math.min(limit, 500))));
    }

    @Transactional(readOnly = true)
    public PaymentResult getForOrder(UUID buyerId, UUID orderId) {
        Payment payment = paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Payment not found for order: " + orderId));
        if (!payment.getBuyerId().equals(buyerId)) {
            throw new SecurityException("Only the buyer may view payment details");
        }
        return new PaymentResult(payment.getId(), payment.getOrderId(), payment.getStatus());
    }
}
