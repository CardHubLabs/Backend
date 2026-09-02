package com.kant.cardhub.payment.application.port;

import java.time.Instant;

public interface PaymentGateway {

    PaymentGatewayResult confirm(ConfirmRequest request);

    PaymentGatewayResult lookup(String paymentKey);

    PaymentGatewayResult cancel(CancelRequest request);

    record ConfirmRequest(
            String paymentKey,
            String tossOrderId,
            long amount,
            String idempotencyKey
    ) {
    }

    record CancelRequest(
            String paymentKey,
            long amount,
            String reason,
            String idempotencyKey
    ) {
    }

    enum ResultStatus {
        APPROVED,
        DECLINED,
        UNKNOWN
    }

    record PaymentGatewayResult(
            ResultStatus status,
            String providerCode,
            Instant approvedAt,
            String paymentKey,
            String orderId,
            Long totalAmount,
            String method,
            String easyPayProvider,
            String providerStatus,
            boolean identityValidationRequired
    ) {
        public PaymentGatewayResult(ResultStatus status, String providerCode, Instant approvedAt) {
            this(status, providerCode, approvedAt, null, null, null, null, null, null, false);
        }

        public static PaymentGatewayResult approved(String code, Instant at) {
            return new PaymentGatewayResult(ResultStatus.APPROVED, code, at);
        }

        public static PaymentGatewayResult providerApproved(
                String code,
                Instant at,
                String paymentKey,
                String orderId,
                Long totalAmount,
                String method,
                String easyPayProvider,
                String providerStatus
        ) {
            return new PaymentGatewayResult(
                    ResultStatus.APPROVED, code, at, paymentKey, orderId, totalAmount,
                    method, easyPayProvider, providerStatus, true);
        }

        public static PaymentGatewayResult declined(String code) {
            return new PaymentGatewayResult(ResultStatus.DECLINED, code, null);
        }

        public static PaymentGatewayResult unknown(String code) {
            return new PaymentGatewayResult(ResultStatus.UNKNOWN, code, null);
        }

        public static PaymentGatewayResult providerUnknown(
                String code,
                String paymentKey,
                String orderId,
                Long totalAmount,
                String method,
                String easyPayProvider,
                String providerStatus
        ) {
            return new PaymentGatewayResult(
                    ResultStatus.UNKNOWN, code, null, paymentKey, orderId, totalAmount,
                    method, easyPayProvider, providerStatus, true);
        }
    }
}
