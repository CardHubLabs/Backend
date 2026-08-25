package com.kant.cardhub.payment.application;

import java.util.UUID;

public record PaymentCommand(
        UUID buyerId,
        UUID orderId,
        String paymentKey,
        String tossOrderId,
        long amount,
        String idempotencyKey
) {
}
