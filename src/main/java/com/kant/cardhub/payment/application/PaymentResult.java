package com.kant.cardhub.payment.application;

import com.kant.cardhub.payment.domain.PaymentStatus;

import java.util.UUID;

public record PaymentResult(UUID paymentId, UUID orderId, PaymentStatus status) {
}
