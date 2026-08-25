package com.kant.cardhub.payment.application;

import com.kant.cardhub.worker.application.ScheduledJobHandler;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class PaymentReconcileJobHandler implements ScheduledJobHandler {

    public static final String JOB_TYPE = "PAYMENT_RECONCILE";

    private final PaymentApplicationService paymentService;

    public PaymentReconcileJobHandler(PaymentApplicationService paymentService) {
        this.paymentService = paymentService;
    }

    @Override
    public String jobType() {
        return JOB_TYPE;
    }

    @Override
    public void handle(String payload) {
        paymentService.reconcile(UUID.fromString(payload));
    }
}
