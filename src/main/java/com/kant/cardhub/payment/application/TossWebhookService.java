package com.kant.cardhub.payment.application;

import com.kant.cardhub.payment.infrastructure.PaymentRepository;
import com.kant.cardhub.worker.application.ScheduledJobService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class TossWebhookService {

    private static final String PAYMENT_STATUS_CHANGED = "PAYMENT_STATUS_CHANGED";
    private static final Set<String> PAYMENT_STATUSES = Set.of(
            "READY", "IN_PROGRESS", "WAITING_FOR_DEPOSIT", "DONE",
            "CANCELED", "PARTIAL_CANCELED", "ABORTED", "EXPIRED"
    );

    private final PaymentRepository paymentRepository;
    private final ScheduledJobService scheduledJobService;
    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;

    public TossWebhookService(
            PaymentRepository paymentRepository,
            ScheduledJobService scheduledJobService,
            JdbcTemplate jdbcTemplate,
            Clock clock
    ) {
        this.paymentRepository = paymentRepository;
        this.scheduledJobService = scheduledJobService;
        this.jdbcTemplate = jdbcTemplate;
        this.clock = clock;
    }

    @Transactional
    public boolean accept(String transmissionId, String eventType, Map<String, Object> data) {
        String normalizedType = eventType == null ? "UNKNOWN" : eventType;
        Map<String, Object> safeData = data == null ? Map.of() : data;
        String paymentKey = stringValue(safeData.get("paymentKey"));
        if (!PAYMENT_STATUS_CHANGED.equals(normalizedType) || paymentKey == null) {
            return false;
        }
        String providerStatus = stringValue(safeData.get("status"));
        if (!PAYMENT_STATUSES.contains(providerStatus)) {
            return false;
        }
        var payment = paymentRepository.findByPaymentKey(paymentKey);
        if (payment.isEmpty()) {
            return false;
        }
        String payloadHash = sha256(normalizedType + '|' + safeData);
        String eventKey = sha256(normalizedType + '|' + payment.orElseThrow().getId() + '|'
                + providerStatus);
        int inserted = jdbcTemplate.update("""
                insert into payment_webhook_events(
                    id, provider, event_key, event_type, payment_id,
                    payment_key_hash, payload_hash, status, received_at
                ) values (?, 'TOSS', ?, ?, ?, ?, ?, ?, ?)
                on conflict (provider, event_key) do nothing
                """,
                UUID.randomUUID(),
                eventKey,
                truncate(normalizedType, 80),
                payment.map(com.kant.cardhub.payment.domain.Payment::getId).orElse(null),
                paymentKey == null ? null : sha256(paymentKey),
                payloadHash,
                "ENQUEUED",
                clock.instant()
        );
        if (inserted == 0) {
            return false;
        }
        UUID paymentId = payment.orElseThrow().getId();
        scheduledJobService.enqueue(
                PaymentReconcileJobHandler.JOB_TYPE,
                paymentId.toString(),
                "toss-reconcile:" + eventKey,
                Instant.now(clock),
                8
        );
        return true;
    }

    private String stringValue(Object value) {
        return value == null || value.toString().isBlank() ? null : value.toString();
    }

    private String truncate(String value, int maxLength) {
        return value.substring(0, Math.min(value.length(), maxLength));
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
