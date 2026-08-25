package com.kant.cardhub.payment.infrastructure;

import com.kant.cardhub.payment.application.port.PaymentGateway;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.context.annotation.Profile;

import java.time.Clock;

@Component
@Profile("!prod")
@ConditionalOnProperty(name = "cardhub.payment.provider", havingValue = "stub", matchIfMissing = true)
public class StubPaymentGateway implements PaymentGateway {

    private final Clock clock;

    public StubPaymentGateway(Clock clock) {
        this.clock = clock;
    }

    @Override
    public PaymentGatewayResult confirm(ConfirmRequest request) {
        if (request.paymentKey().startsWith("unknown_")) {
            return PaymentGatewayResult.unknown("STUB_TIMEOUT");
        }
        if (request.paymentKey().startsWith("fail_")) {
            return PaymentGatewayResult.declined("STUB_DECLINED");
        }
        return PaymentGatewayResult.approved("STUB_APPROVED", clock.instant());
    }

    @Override
    public PaymentGatewayResult lookup(String paymentKey) {
        if (paymentKey.startsWith("fail_")) {
            return PaymentGatewayResult.declined("STUB_DECLINED");
        }
        return PaymentGatewayResult.approved("STUB_RECONCILED", clock.instant());
    }

    @Override
    public PaymentGatewayResult cancel(CancelRequest request) {
        if (request.paymentKey().startsWith("refund_unknown_")) {
            return PaymentGatewayResult.unknown("STUB_REFUND_TIMEOUT");
        }
        return PaymentGatewayResult.approved("STUB_CANCELLED", clock.instant());
    }
}
