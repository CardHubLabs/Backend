package com.kant.cardhub.payment.infrastructure;

import com.kant.cardhub.payment.application.port.PaymentGateway;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class TossPaymentGatewayTest {

    private final TossPaymentGateway gateway = new TossPaymentGateway(
                RestClient.builder(),
                new TossPaymentProperties(
                        "toss", "https://api.tosspayments.test", "test_secret",
                        Duration.ofSeconds(1), Duration.ofSeconds(1)
                ),
                Clock.fixed(Instant.parse("2026-08-25T00:00:00Z"), ZoneOffset.UTC)
        );

    @Test
    void ambiguousConfirmFailureNeverReleasesTheReservation() {
        PaymentGateway.PaymentGatewayResult result =
                gateway.classifyConfirmFailure("ALREADY_PROCESSED_PAYMENT");

        assertThat(result.status()).isEqualTo(PaymentGateway.ResultStatus.UNKNOWN);
    }

    @Test
    void partialCancellationLookupRemainsUnknown() {
        PaymentGateway.PaymentGatewayResult result =
                gateway.classifyProviderStatus("PARTIAL_CANCELED", null, 5_000L);

        assertThat(result.status()).isEqualTo(PaymentGateway.ResultStatus.UNKNOWN);
    }

    @Test
    void refundIsApprovedOnlyWhenTheRemainingBalanceIsZero() {
        PaymentGateway.PaymentGatewayResult result = gateway.classifyCancelResponse(
                new TossPaymentGateway.TossPaymentResponse("CANCELED", null, 0L));

        assertThat(result.status()).isEqualTo(PaymentGateway.ResultStatus.APPROVED);
    }

    @Test
    void approvedPaymentCarriesEveryIdentityFieldForServerValidation() {
        PaymentGateway.PaymentGatewayResult result = gateway.map(
                new TossPaymentGateway.TossPaymentResponse(
                        "payment-key", "CH-123456", 13_000L, "간편결제",
                        new TossPaymentGateway.TossEasyPay("토스페이"),
                        "DONE", "2026-08-25T09:00:00+09:00", 13_000L));

        assertThat(result.status()).isEqualTo(PaymentGateway.ResultStatus.APPROVED);
        assertThat(result.identityValidationRequired()).isTrue();
        assertThat(result.paymentKey()).isEqualTo("payment-key");
        assertThat(result.orderId()).isEqualTo("CH-123456");
        assertThat(result.totalAmount()).isEqualTo(13_000L);
        assertThat(result.method()).isEqualTo("간편결제");
        assertThat(result.easyPayProvider()).isEqualTo("토스페이");
    }

    @Test
    void notFoundLookupDoesNotDefinitivelyFailAnUnknownPayment() {
        PaymentGateway.PaymentGatewayResult result =
                gateway.classifyLookupFailure(404, "NOT_FOUND_PAYMENT");

        assertThat(result.status()).isEqualTo(PaymentGateway.ResultStatus.UNKNOWN);
    }
}
