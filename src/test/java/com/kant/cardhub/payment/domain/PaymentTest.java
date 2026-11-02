package com.kant.cardhub.payment.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentTest {

    private static final Instant NOW = Instant.parse("2026-08-25T00:00:00Z");

    @Test
    void unknownPaymentCanLaterConvergeToApproved() {
        Payment payment = payment();
        payment.unknown("TIMEOUT", NOW.plusSeconds(1));

        payment.approve("DONE", NOW.plusSeconds(2), NOW.plusSeconds(2));

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.APPROVED);
        assertThat(payment.getApprovedAt()).isEqualTo(NOW.plusSeconds(2));
    }

    @Test
    void definitivePaymentCannotBeOverwritten() {
        Payment payment = payment();
        payment.fail("DECLINED", NOW.plusSeconds(1));

        assertThatThrownBy(() -> payment.approve("DONE", NOW.plusSeconds(2), NOW.plusSeconds(2)))
                .isInstanceOf(IllegalStateException.class);
        payment.unknown("LATE_TIMEOUT", NOW.plusSeconds(3));
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
    }

    @Test
    void idempotentRequestMustMatchEveryMaterialField() {
        UUID orderId = UUID.randomUUID();
        UUID buyerId = UUID.randomUUID();
        Payment payment = Payment.prepare(orderId, buyerId, "idem", "key", "toss-order", 1_000, NOW);

        assertThat(payment.requestMatches(orderId, buyerId, "key", "toss-order", 1_000)).isTrue();
        assertThat(payment.requestMatches(orderId, buyerId, "key", "toss-order", 1_001)).isFalse();
    }

    private Payment payment() {
        return Payment.prepare(
                UUID.randomUUID(), UUID.randomUUID(), "idem", "key", "toss-order", 1_000, NOW);
    }
}
