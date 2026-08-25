package com.kant.cardhub.payment.application;

import com.kant.cardhub.payment.application.port.PaymentGateway;
import com.kant.cardhub.payment.domain.RefundStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.UUID;

@Service
public class RefundProcessingService {

    private final RefundTransactionService transactionService;
    private final PaymentGateway paymentGateway;

    public RefundProcessingService(RefundTransactionService transactionService, PaymentGateway paymentGateway) {
        this.transactionService = transactionService;
        this.paymentGateway = paymentGateway;
    }

    public RefundStatus approve(UUID refundId) {
        RefundTransactionService.PreparedRefund prepared = transactionService.prepare(refundId);
        PaymentGateway.PaymentGatewayResult result;
        try {
            result = paymentGateway.cancel(new PaymentGateway.CancelRequest(
                    prepared.paymentKey(), prepared.amount(), prepared.reason(), "refund:" + refundId));
        } catch (RuntimeException exception) {
            result = PaymentGateway.PaymentGatewayResult.unknown("GATEWAY_CANCEL_FAILED");
        }
        return transactionService.finalizeRefund(refundId, result);
    }

    @Transactional(readOnly = true)
    public List<UUID> findRecoverable(int limit) {
        return transactionService.findRecoverable(Math.max(1, Math.min(limit, 500)));
    }
}
