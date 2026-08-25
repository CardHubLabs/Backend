package com.kant.cardhub.payment.application;

import com.kant.cardhub.payment.domain.Payment;
import com.kant.cardhub.payment.infrastructure.PaymentRepository;
import com.kant.cardhub.worker.application.ScheduledJobService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class TossWebhookServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-25T00:00:00Z");

    private PaymentRepository paymentRepository;
    private ScheduledJobService scheduledJobService;
    private JdbcTemplate jdbcTemplate;
    private TossWebhookService service;

    @BeforeEach
    void setUp() {
        paymentRepository = mock(PaymentRepository.class);
        scheduledJobService = mock(ScheduledJobService.class);
        jdbcTemplate = mock(JdbcTemplate.class);
        service = new TossWebhookService(
                paymentRepository,
                scheduledJobService,
                jdbcTemplate,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void rejectsIrrelevantOrUnboundedProviderStatusesBeforeWriting() {
        assertThat(service.accept(
                "transmission", "PAYMENT_STATUS_CHANGED",
                Map.of("paymentKey", "known-key", "status", "ATTACKER_VALUE")
        )).isFalse();

        verifyNoInteractions(paymentRepository, scheduledJobService, jdbcTemplate);
    }

    @Test
    void knownPaymentEnqueuesProviderReconciliationWithoutTrustingThePayload() {
        Payment payment = payment();
        when(paymentRepository.findByPaymentKey("known-key")).thenReturn(Optional.of(payment));
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);

        boolean accepted = service.accept(
                "transmission", "PAYMENT_STATUS_CHANGED",
                Map.of("paymentKey", "known-key", "status", "DONE")
        );

        assertThat(accepted).isTrue();
        verify(scheduledJobService).enqueue(
                eq(PaymentReconcileJobHandler.JOB_TYPE),
                eq(payment.getId().toString()),
                startsWith("toss-reconcile:"),
                eq(NOW),
                eq(8)
        );
    }

    @Test
    void duplicateProviderStateDoesNotEnqueueAgain() {
        Payment payment = payment();
        when(paymentRepository.findByPaymentKey("known-key")).thenReturn(Optional.of(payment));
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(0);

        boolean accepted = service.accept(
                "transmission", "PAYMENT_STATUS_CHANGED",
                Map.of("paymentKey", "known-key", "status", "DONE")
        );

        assertThat(accepted).isFalse();
        verify(scheduledJobService, never()).enqueue(
                anyString(), anyString(), anyString(), any(), anyInt());
    }

    private Payment payment() {
        return Payment.prepare(
                UUID.randomUUID(), UUID.randomUUID(), "idem", "known-key", "toss-order",
                10_000L, NOW.minusSeconds(1)
        );
    }
}
